package club.skidware.kconfig.writer

import club.skidware.kconfig.annotation.CommentPlacement
import club.skidware.kconfig.annotation.Transient
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SerializerRegistry
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Writes serialized configuration data to YAML format with comment support.
 *
 * This writer produces clean, human-readable YAML output and supports two comment
 * placement modes:
 *
 * - **[CommentPlacement.INLINE]**: Single-line comments are placed on the same line as
 *   the value (e.g., `port: 8080  # The server port`). Falls back to ABOVE placement
 *   for multi-line comments, lists, and map sections.
 * - **[CommentPlacement.ABOVE]**: Comments are placed on separate lines above the
 *   property. An empty line is inserted before section-level comments for readability.
 *
 * Scalar values are automatically quoted when they contain special YAML characters
 * (`:`, `#`, `{`, `}`, etc.), look like booleans (`true`/`false`), nulls, or numbers.
 *
 * Example:
 * ```kotlin
 * val data = mapOf("server" to mapOf("port" to 8080, "host" to "localhost"))
 * val comments = mapOf("server.port" to CommentData(listOf("The server port"), CommentPlacement.INLINE))
 *
 * YamlWriter.write(File("config.yml"), data, comments)
 * // Produces:
 * // server:
 * //   port: 8080  # The server port
 * //   host: localhost
 * ```
 *
 * @see CommentExtractor
 * @see Serializer
 * @see CommentPlacement
 * @since 1.0
 */
object YamlWriter {

    /**
     * Writes serialized configuration [data] to a [file] in YAML format.
     *
     * Creates parent directories if they do not exist. The file is written
     * with UTF-8 encoding.
     *
     * @param file The target file to write to. Parent directories are created automatically.
     * @param data The serialized configuration data as a string-keyed map.
     * @param comments Optional comment metadata to embed in the output.
     * @see writeToString
     * @since 1.0
     */
    fun write(file: File, data: Map<String, Any?>, comments: Map<String, CommentData> = emptyMap()) {
        file.parentFile?.mkdirs()
        file.writeText(this.writeToString(data, comments), Charsets.UTF_8)
    }

    /**
     * Renders serialized configuration [data] to a YAML-formatted string.
     *
     * This is the in-memory variant of [write] and is used internally by
     * [YamlConfigManager.toDebugString] for debug output with secret masking.
     *
     * Example:
     * ```kotlin
     * val yaml = YamlWriter.writeToString(
     *     data = mapOf("name" to "MyApp", "debug" to true),
     *     comments = mapOf("debug" to CommentData(listOf("Enable debug mode"), CommentPlacement.ABOVE))
     * )
     * // Returns:
     * // name: MyApp
     * //
     * // # Enable debug mode
     * // debug: true
     * ```
     *
     * @param data The serialized configuration data as a string-keyed map.
     * @param comments Optional comment metadata to embed in the output.
     * @return The YAML-formatted string.
     * @see write
     * @since 1.0
     */
    fun writeToString(data: Map<String, Any?>, comments: Map<String, CommentData> = emptyMap()): String {
        val sb = StringBuilder()
        this.writeMap(sb, data, comments, indent = 0, prefix = "")
        return sb.toString()
    }

    private fun writeMap(
        sb: StringBuilder,
        map: Map<String, Any?>,
        comments: Map<String, CommentData>,
        indent: Int,
        prefix: String
    ) {
        var first = true

        for ((key, value) in map) {
            val fullPath = if (prefix.isEmpty()) key else "$prefix.$key"
            val comment = comments[fullPath]

            this.writeMapEntry(sb, key, value, comment, comments, indent, fullPath, first)
            first = false
        }
    }

    private fun writeMapEntry(
        sb: StringBuilder,
        key: String,
        value: Any?,
        comment: CommentData?,
        comments: Map<String, CommentData>,
        indent: Int,
        fullPath: String,
        first: Boolean
    ) {
        val indentStr = " ".repeat(indent)

        if (!first && comment != null && this.isSection(value)) {
            sb.appendLine()
        }

        this.writeAboveComment(sb, comment, value, indentStr, first)

        when {
            value is Map<*, *> && value.isNotEmpty() -> {
                this.writeNestedMapValue(sb, key, value, comments, indent, fullPath, indentStr)
            }
            value is List<*> -> {
                this.writeListValue(sb, key, value, comment, indentStr)
            }
            else -> {
                this.writeScalarEntry(sb, key, value, comment, indentStr)
            }
        }
    }

    private fun writeAboveComment(
        sb: StringBuilder,
        comment: CommentData?,
        value: Any?,
        indentStr: String,
        first: Boolean
    ) {
        if (comment == null) return

        val isMultiline = comment.lines.size > 1
        val isSection = this.isSection(value)

        if (comment.placement == CommentPlacement.ABOVE || isMultiline || isSection) {
            if (!first && !isSection) sb.appendLine()
            for (line in comment.lines) {
                sb.appendLine("${indentStr}# $line")
            }
        }
    }

    private fun writeNestedMapValue(
        sb: StringBuilder,
        key: String,
        value: Map<*, *>,
        comments: Map<String, CommentData>,
        indent: Int,
        fullPath: String,
        indentStr: String
    ) {
        sb.appendLine("${indentStr}${key}:")
        @Suppress("UNCHECKED_CAST")
        this.writeMap(sb, value as Map<String, Any?>, comments, indent + 2, fullPath)
    }

    private fun writeListValue(
        sb: StringBuilder,
        key: String,
        value: List<*>,
        comment: CommentData?,
        indentStr: String
    ) {
        if (value.isEmpty()) {
            sb.appendLine("${indentStr}${key}: []")
        } else if (value.all { it is Map<*, *> }) {
            sb.appendLine("${indentStr}${key}:")
            for (item in value) {
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val entries = (item as Map<String, Any?>).entries.toList()
                    if (entries.isNotEmpty()) {
                        val (firstKey, firstValue) = entries.first()
                        sb.appendLine("${indentStr}  - ${firstKey}: ${this.formatScalar(firstValue)}")
                        for (i in 1 until entries.size) {
                            val (ek, ev) = entries[i]
                            sb.appendLine("${indentStr}    ${ek}: ${this.formatScalar(ev)}")
                        }
                    }
                }
            }
        } else {
            sb.appendLine("${indentStr}${key}:")
            for (item in value) {
                sb.appendLine("${indentStr}  - ${this.formatScalar(item)}")
            }
        }
        this.appendInlineComment(sb, comment, value)
    }

    private fun writeScalarEntry(
        sb: StringBuilder,
        key: String,
        value: Any?,
        comment: CommentData?,
        indentStr: String
    ) {
        val formatted = this.formatScalar(value)
        if (comment != null && comment.placement == CommentPlacement.INLINE && comment.lines.size == 1 && !this.isSection(value)) {
            sb.appendLine("${indentStr}${key}: $formatted  # ${comment.lines[0]}")
        } else {
            sb.appendLine("${indentStr}${key}: $formatted")
        }
    }

    private fun appendInlineComment(sb: StringBuilder, comment: CommentData?, value: Any?) {
    }

    private fun isSection(value: Any?): Boolean {
        return value is Map<*, *> && value.isNotEmpty()
    }

    private fun formatScalar(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> this.quoteIfNeeded(value)
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Enum<*> -> value.name
            else -> this.quoteIfNeeded(value.toString())
        }
    }

    private fun quoteIfNeeded(s: String): String {
        if (s.isEmpty()) return "\"\""
        val needsQuoting = this.containsSpecialYamlChars(s) ||
            this.looksLikeReservedLiteral(s) ||
            this.looksLikeNumber(s) ||
            this.hasLeadingOrTrailingSpaces(s)
        return if (needsQuoting) "\"${this.escapeString(s)}\"" else s
    }

    private fun containsSpecialYamlChars(s: String): Boolean {
        return s.contains(':') || s.contains('#') || s.contains('{') || s.contains('}') ||
            s.contains('[') || s.contains(']') || s.contains(',') || s.contains('&') || s.contains('*') ||
            s.contains('?') || s.contains('|') || s.contains('>') || s.contains('!') || s.contains('%') ||
            s.contains('@') || s.contains('`') || s.contains('"') || s.contains('\'')
    }

    private fun looksLikeReservedLiteral(s: String): Boolean {
        return s.equals("true", ignoreCase = true) || s.equals("false", ignoreCase = true) ||
            s.equals("null", ignoreCase = true) || s.equals("~")
    }

    private fun looksLikeNumber(s: String): Boolean {
        return s.toDoubleOrNull() != null || s.toLongOrNull() != null
    }

    private fun hasLeadingOrTrailingSpaces(s: String): Boolean {
        return s.startsWith(' ') || s.endsWith(' ')
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")
    }
}

/**
 * Converts typed configuration objects into serialized map representations
 * suitable for YAML output.
 *
 * The serializer walks the primary constructor parameters of data classes in
 * declaration order, producing a [LinkedHashMap] that preserves field ordering.
 * Properties annotated with [@Transient][Transient] are excluded from output.
 *
 * Supported value types:
 * - Primitives: [String], [Number], [Boolean]
 * - Enums: serialized by their [Enum.name]
 * - [SecretString]: serialized by exposing the underlying value
 * - Collections: [List], [Set], [Map]
 * - Custom types: dispatched to [SerializerRegistry] if a [TypeSerializer] is registered
 * - Nested data classes: recursively serialized
 * - All other types: converted via [Any.toString]
 *
 * Example:
 * ```kotlin
 * data class ServerConfig(
 *     val host: String = "localhost",
 *     val port: Int = 8080,
 *     @Transient val runtimeOnly: String = "ignored"
 * )
 *
 * val config = ServerConfig()
 * val map = Serializer.serialize(config, registry)
 * // map = {"host" -> "localhost", "port" -> 8080}
 * // Note: "runtimeOnly" is excluded because of @Transient
 * ```
 *
 * @see YamlWriter
 * @see SerializerRegistry
 * @see Transient
 * @since 1.0
 */
object Serializer {

    /**
     * Serializes a configuration [instance] into a map of property names to values.
     *
     * The resulting map preserves constructor parameter order and excludes
     * properties annotated with [@Transient][Transient]. Nested data classes
     * are recursively serialized, and custom types are dispatched through
     * the provided [registry].
     *
     * @param T The configuration type, must be a non-null type.
     * @param instance The configuration object to serialize.
     * @param registry The [SerializerRegistry] used to resolve custom [TypeSerializer]
     *   implementations for non-standard types.
     * @return A [Map] of property names to their serialized values. Ordering
     *   matches the primary constructor parameter order.
     * @see YamlWriter.write
     * @since 1.0
     */
    fun <T : Any> serialize(instance: T, registry: SerializerRegistry): Map<String, Any?> {
        return this.serializeObject(instance, registry)
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializeObject(instance: Any, registry: SerializerRegistry): LinkedHashMap<String, Any?> {
        val klass = instance::class
        val constructor = klass.primaryConstructor ?: return linkedMapOf()
        val properties = klass.memberProperties.associateBy { it.name }
        val result = LinkedHashMap<String, Any?>()

        for (param in constructor.parameters) {
            val name = param.name ?: continue

            if (param.findAnnotation<Transient>() != null) continue

            val prop = properties[name] ?: continue
            val value = prop.getter.call(instance)

            result[name] = this.serializeValue(value, registry)
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializeValue(value: Any?, registry: SerializerRegistry): Any? {
        return when (value) {
            null -> null
            is String -> value
            is Number -> value
            is Boolean -> value
            is Enum<*> -> value.name
            is SecretString -> value.expose()
            is List<*> -> value.map { this.serializeValue(it, registry) }
            is Set<*> -> value.map { this.serializeValue(it, registry) }
            is Map<*, *> -> {
                val result = LinkedHashMap<String, Any?>()
                for ((k, v) in value) {
                    val keyStr = when (k) {
                        is Enum<*> -> k.name
                        else -> k.toString()
                    }
                    result[keyStr] = this.serializeValue(v, registry)
                }
                result
            }
            else -> {
                val klass = value::class
                if (registry.has(klass)) {
                    val serializer = registry.get(klass as KClass<Any>)!!
                    serializer.serialize(value)
                } else if (klass.isData) {
                    this.serializeObject(value, registry)
                } else {
                    value.toString()
                }
            }
        }
    }
}
