package club.skidware.kconfig.reader

import club.skidware.kconfig.annotation.*
import club.skidware.kconfig.annotation.Transient
import club.skidware.kconfig.error.ConfigError
import club.skidware.kconfig.error.ConfigErrorCollector
import club.skidware.kconfig.error.closestMatch
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SerializerRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

/**
 * Reflection-based deserializer that converts a [Map] (from parsed YAML) into Kotlin data class instances.
 *
 * ## Algorithm
 *
 * For each target data class the deserializer:
 * 1. **Resolves metadata** -- reflects on the primary constructor and caches parameter info
 *    in a [ConcurrentHashMap] for fast repeated access.
 * 2. **Detects unknown keys** -- any YAML key that does not match a constructor parameter
 *    (except the reserved `"configVersion"`) is reported as [ConfigError.UnknownKey], with a
 *    Levenshtein-based "did you mean ...?" suggestion when a close match exists.
 * 3. **Iterates constructor parameters** and for each one:
 *    - **@Transient** -- skips the parameter entirely; Kotlin uses its default value.
 *    - **Key lookup** -- tries the parameter name, then any legacy names from **@MigrateFrom**.
 *    - **Missing value** -- if the parameter is optional (has a Kotlin default) it is skipped;
 *      if nullable it is set to `null`; otherwise a [ConfigError.MissingRequired] is recorded.
 *    - **Deserialization** -- delegates to [deserializeValue] which handles primitives, enums,
 *      `List`, `Set`, `Map`, [SecretString][club.skidware.kconfig.serializer.SecretString],
 *      custom serializers from the [SerializerRegistry], and recursive nested data classes.
 *    - **@Range** / **@Pattern** validation -- if the constraint is violated, an error is
 *      recorded and the field falls back to its Kotlin default (when available).
 * 4. **Constructs the instance** via `KFunction.callBy`, which honours Kotlin default values
 *    for any parameter not explicitly provided.
 *
 * ## Supported types
 *
 * | Category         | Types                                                      |
 * |------------------|------------------------------------------------------------|
 * | Primitives       | `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`      |
 * | Special          | `SecretString`, any `enum class`                           |
 * | Collections      | `List<T>`, `Set<T>`, `Map<K, V>` (recursively typed)       |
 * | Custom           | Any type registered in [SerializerRegistry]                |
 * | Nested           | Any Kotlin `data class` (deserialized recursively)         |
 *
 * ## Annotation interactions
 *
 * - **@Transient**: Field is skipped; always uses Kotlin default value.
 * - **@MigrateFrom**: Falls back to legacy key names when the current key is missing.
 * - **@Range**: Validates numeric values are within bounds; falls back to default on violation.
 * - **@Pattern**: Validates string values against a regex; falls back to default on violation.
 *
 * ## Caching
 *
 * Constructor metadata ([ClassMetadata]) is cached in a [ConcurrentHashMap] keyed by [KClass],
 * so reflection costs are paid only once per type even across multiple deserialization calls.
 *
 * Example:
 * ```kotlin
 * val registry = SerializerRegistry()
 * BuiltinSerializers.registerAll(registry)
 * val deserializer = Deserializer(registry)
 * val errors = ConfigErrorCollector()
 *
 * val config = deserializer.deserialize(MyConfig::class, yamlMap, errors)
 * if (errors.hasErrors()) {
 *     println(ConfigErrorFormatter().format(errors.all()))
 * }
 * ```
 *
 * @param registry The [SerializerRegistry] used to resolve custom type serializers.
 * @see SerializerRegistry
 * @see ConfigErrorCollector
 * @see EnvOverrideResolver
 * @since 1.0
 */
class Deserializer(private val registry: SerializerRegistry) {

    private val metadataCache = ConcurrentHashMap<KClass<*>, ClassMetadata>()

    /**
     * Cached reflection metadata for a data class, capturing its primary constructor
     * and the set of valid parameter names.
     *
     * @param constructor The primary constructor function.
     * @param parameters Ordered list of constructor parameters.
     * @param paramNames Set of parameter names, used for fast unknown-key detection.
     */
    data class ClassMetadata(
        val constructor: KFunction<*>,
        val parameters: List<KParameter>,
        val paramNames: Set<String>
    )

    /**
     * Returns cached [ClassMetadata] for the given class, creating and caching it on first access.
     *
     * @param klass The data class to reflect on.
     * @return The cached metadata.
     * @throws IllegalArgumentException If [klass] has no primary constructor.
     */
    private fun getMetadata(klass: KClass<*>): ClassMetadata {
        return this.metadataCache.getOrPut(klass) {
            val constructor = klass.primaryConstructor
                ?: throw IllegalArgumentException("Class ${klass.simpleName} has no primary constructor")
            ClassMetadata(
                constructor = constructor,
                parameters = constructor.parameters,
                paramNames = constructor.parameters.mapNotNull { it.name }.toSet()
            )
        }
    }

    /**
     * Deserializes a YAML-sourced [map] into an instance of [klass].
     *
     * The method walks the primary constructor of [klass], resolves each parameter from [map]
     * (honouring annotations), validates constraints, and constructs the final instance.
     * Any errors encountered are accumulated in [errors] rather than thrown, so that the
     * caller receives a complete diagnostic report.
     *
     * @param T The target data class type.
     * @param klass The [KClass] token for [T].
     * @param map The key-value map produced by [YamlReader] (or equivalent).
     * @param errors The collector that receives all [ConfigError] instances.
     * @param path Dot-separated prefix for nested paths (empty string at the root level).
     * @return A fully constructed instance of [T], or `null` if construction failed
     *         (e.g. missing required fields, no primary constructor).
     * @see ConfigErrorCollector
     */
    fun <T : Any> deserialize(
        klass: KClass<T>,
        map: Map<String, Any?>,
        errors: ConfigErrorCollector,
        path: String = ""
    ): T? {
        val metadata = try {
            this.getMetadata(klass)
        } catch (e: IllegalArgumentException) {
            errors.add(ConfigError.UnknownType(path, klass.simpleName ?: "unknown"))
            return null
        }

        this.reportUnknownKeys(map, metadata.paramNames, errors, path)
        val args = this.resolveConstructorArgs(metadata.parameters, map, errors, path)

        return try {
            @Suppress("UNCHECKED_CAST")
            metadata.constructor.callBy(args) as T
        } catch (e: Exception) {
            errors.add(ConfigError.InvalidValue(path, null, klass.simpleName ?: "unknown", e.message))
            null
        }
    }

    private fun reportUnknownKeys(
        map: Map<String, Any?>,
        validNames: Set<String>,
        errors: ConfigErrorCollector,
        path: String
    ) {
        for (key in map.keys) {
            if (key !in validNames && key != "configVersion") {
                val suggestion = validNames.closestMatch(key)
                errors.add(ConfigError.UnknownKey(if (path.isEmpty()) key else "$path.$key", suggestion))
            }
        }
    }

    private fun resolveConstructorArgs(
        parameters: List<KParameter>,
        map: Map<String, Any?>,
        errors: ConfigErrorCollector,
        path: String
    ): MutableMap<KParameter, Any?> {
        val args = mutableMapOf<KParameter, Any?>()

        for (param in parameters) {
            val name = param.name ?: continue
            val fullPath = if (path.isEmpty()) name else "$path.$name"

            if (param.findAnnotation<Transient>() != null) continue

            val raw = this.resolveRawValue(param, name, map)

            if (raw == null && name !in map && !this.hasLegacyKey(param, map)) {
                this.handleMissingValue(param, fullPath, args, errors)
                continue
            }

            val deserialized = this.deserializeValue(raw, param.type, errors, fullPath)

            if (this.failsValidation(param, deserialized, fullPath, errors)) {
                if (param.isOptional) continue
            }

            if (deserialized != null || param.type.isMarkedNullable) {
                args[param] = deserialized
            } else if (param.isOptional) {
                continue
            }
        }

        return args
    }

    private fun resolveRawValue(param: KParameter, name: String, map: Map<String, Any?>): Any? {
        val direct = map[name]
        if (direct != null || name in map) return direct

        val migrateFrom = param.findAnnotation<MigrateFrom>() ?: return null
        for (oldKey in migrateFrom.oldKeys) {
            val oldValue = this.resolveNestedKey(map, oldKey)
            if (oldValue != null) return oldValue
        }
        return null
    }

    private fun hasLegacyKey(param: KParameter, map: Map<String, Any?>): Boolean {
        val migrateFrom = param.findAnnotation<MigrateFrom>() ?: return false
        return migrateFrom.oldKeys.any { this.resolveNestedKey(map, it) != null }
    }

    private fun handleMissingValue(
        param: KParameter,
        fullPath: String,
        args: MutableMap<KParameter, Any?>,
        errors: ConfigErrorCollector
    ) {
        when {
            param.isOptional -> { /* Kotlin default */ }
            param.type.isMarkedNullable -> args[param] = null
            else -> errors.add(ConfigError.MissingRequired(fullPath, this.formatType(param.type)))
        }
    }

    private fun failsValidation(
        param: KParameter,
        value: Any?,
        fullPath: String,
        errors: ConfigErrorCollector
    ): Boolean {
        val rangeAnn = param.findAnnotation<Range>()
        if (rangeAnn != null && value is Number) {
            val numVal = value.toDouble()
            if (numVal < rangeAnn.min || numVal > rangeAnn.max) {
                errors.add(ConfigError.OutOfRange(fullPath, value, rangeAnn.min, rangeAnn.max, null))
                return true
            }
        }

        val patternAnn = param.findAnnotation<Pattern>()
        if (patternAnn != null && value is String) {
            if (!Regex(patternAnn.regex).matches(value)) {
                errors.add(ConfigError.PatternMismatch(fullPath, value, patternAnn.regex, patternAnn.description.ifEmpty { null }))
                return true
            }
        }

        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeValue(
        raw: Any?,
        type: KType,
        errors: ConfigErrorCollector,
        path: String
    ): Any? {
        if (raw == null) return null

        val classifier = type.classifier

        this.deserializePrimitive(raw, classifier, errors, path)?.let { return it }
        if (classifier == SecretString::class) return SecretString(raw.toString())

        val klass = classifier as? KClass<*> ?: run {
            errors.add(ConfigError.UnknownType(path, raw::class.simpleName ?: "unknown"))
            return null
        }

        return when {
            klass.java.isEnum -> this.deserializeEnum(raw, klass, errors, path)
            klass.isSubclassOf(List::class) -> this.deserializeList(raw, type, errors, path)
            klass.isSubclassOf(Set::class) -> this.deserializeSet(raw, type, errors, path)
            klass.isSubclassOf(Map::class) -> this.deserializeMap(raw, type, errors, path)
            this.registry.has(klass) -> this.deserializeCustom(raw, klass, errors, path)
            klass.isData -> this.deserializeNested(raw, klass, errors, path)
            else -> {
                errors.add(ConfigError.UnknownType(path, klass.simpleName ?: "unknown"))
                null
            }
        }
    }

    private sealed class PrimitiveResult {
        data class Success(val value: Any) : PrimitiveResult()
        data object NotPrimitive : PrimitiveResult()
    }

    private fun deserializePrimitive(
        raw: Any,
        classifier: KClassifier?,
        errors: ConfigErrorCollector,
        path: String
    ): Any? {
        return when (classifier) {
            String::class -> raw.toString()
            Int::class -> this.coerceNumber(raw, "Int", Number::toInt, String::toIntOrNull, errors, path)
            Long::class -> this.coerceNumber(raw, "Long", Number::toLong, String::toLongOrNull, errors, path)
            Double::class -> this.coerceNumber(raw, "Double", Number::toDouble, String::toDoubleOrNull, errors, path)
            Float::class -> this.coerceNumber(raw, "Float", Number::toFloat, String::toFloatOrNull, errors, path)
            Boolean::class -> when (raw) {
                is Boolean -> raw
                is String -> raw.lowercase().toBooleanStrictOrNull() ?: run {
                    errors.add(ConfigError.InvalidValue(path, raw, "Boolean")); null
                }
                is Number -> raw.toInt() != 0
                else -> run { errors.add(ConfigError.InvalidValue(path, raw, "Boolean")); null }
            }
            else -> null
        }
    }

    private fun <T> coerceNumber(
        raw: Any,
        typeName: String,
        fromNumber: (Number) -> T,
        fromString: (String) -> T?,
        errors: ConfigErrorCollector,
        path: String
    ): T? {
        return when (raw) {
            is Number -> fromNumber(raw)
            is String -> fromString(raw) ?: run {
                errors.add(ConfigError.InvalidValue(path, raw, typeName)); null
            }
            else -> run { errors.add(ConfigError.InvalidValue(path, raw, typeName)); null }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeEnum(
        raw: Any,
        klass: KClass<*>,
        errors: ConfigErrorCollector,
        path: String
    ): Any? {
        val rawStr = raw.toString()
        val enumConstants = klass.java.enumConstants as Array<Enum<*>>
        val matched = enumConstants.firstOrNull { it.name.equals(rawStr, ignoreCase = true) }
        if (matched != null) return matched

        val names = enumConstants.map { it.name }
        val suggestion = names.closestMatch(rawStr)
        errors.add(ConfigError.InvalidValue(path, raw, "one of ${names.joinToString()}", suggestion?.let { "did you mean '$it'?" }))
        return null
    }

    private fun deserializeList(
        raw: Any,
        type: KType,
        errors: ConfigErrorCollector,
        path: String
    ): List<Any?>? {
        val list = raw as? List<*> ?: run {
            errors.add(ConfigError.InvalidValue(path, raw, "List")); return null
        }
        val elementType = type.arguments.firstOrNull()?.type ?: return list.toList()
        return list.mapIndexed { index, item ->
            this.deserializeValue(item, elementType, errors, "$path[$index]")
        }
    }

    private fun deserializeSet(
        raw: Any,
        type: KType,
        errors: ConfigErrorCollector,
        path: String
    ): Set<Any?>? {
        val list = raw as? List<*> ?: run {
            errors.add(ConfigError.InvalidValue(path, raw, "Set")); return null
        }
        val elementType = type.arguments.firstOrNull()?.type ?: return list.toSet()
        return list.mapIndexed { index, item ->
            this.deserializeValue(item, elementType, errors, "$path[$index]")
        }.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeMap(
        raw: Any,
        type: KType,
        errors: ConfigErrorCollector,
        path: String
    ): Map<Any?, Any?>? {
        val mapRaw = raw as? Map<*, *> ?: run {
            errors.add(ConfigError.InvalidValue(path, raw, "Map")); return null
        }
        val keyType = type.arguments.getOrNull(0)?.type
        val valueType = type.arguments.getOrNull(1)?.type
        if (keyType == null || valueType == null) return mapRaw as Map<Any?, Any?>

        val result = LinkedHashMap<Any?, Any?>()
        for ((k, v) in mapRaw) {
            result[this.deserializeValue(k, keyType, errors, "$path.key($k)")] =
                this.deserializeValue(v, valueType, errors, "$path.$k")
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeCustom(
        raw: Any,
        klass: KClass<*>,
        errors: ConfigErrorCollector,
        path: String
    ): Any? {
        val serializer = this.registry.get(klass as KClass<Any>)!!
        return try {
            serializer.deserialize(raw)
        } catch (e: Exception) {
            errors.add(ConfigError.InvalidValue(path, raw, klass.simpleName ?: "unknown", e.message))
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun deserializeNested(
        raw: Any,
        klass: KClass<*>,
        errors: ConfigErrorCollector,
        path: String
    ): Any? {
        val nestedMap = raw as? Map<String, Any?> ?: run {
            errors.add(ConfigError.InvalidValue(path, raw, "Map (for ${klass.simpleName})"))
            return null
        }
        return this.deserialize(klass, nestedMap, errors, path)
    }

    /**
     * Resolves a possibly dot-separated key against a nested map structure.
     *
     * For simple keys (no dots), this is equivalent to `map[key]`. For compound keys
     * like `"database.credentials.password"`, each segment is used to descend into
     * nested maps. Returns `null` if any segment is missing or if an intermediate value
     * is not a [Map].
     *
     * This supports the [@MigrateFrom][club.skidware.kconfig.annotation.MigrateFrom] annotation
     * where legacy keys may reference values that have been restructured into nested sections.
     *
     * @param map The root map to search.
     * @param key The key to resolve, optionally dot-separated.
     * @return The resolved value, or `null` if not found.
     */
    private fun resolveNestedKey(map: Map<String, Any?>, key: String): Any? {
        if (key.contains('.')) {
            val parts = key.split('.')
            var current: Any? = map
            for (part in parts) {
                current = (current as? Map<*, *>)?.get(part) ?: return null
            }
            return current
        }
        return map[key]
    }

    /**
     * Returns a human-readable simple name for the given [KType].
     *
     * Used in [ConfigError.MissingRequired] messages to tell the user what type was expected.
     *
     * @param type The Kotlin type to format.
     * @return The simple class name, or `"unknown"` if the classifier cannot be resolved.
     */
    private fun formatType(type: KType): String {
        val classifier = type.classifier
        return when {
            classifier is KClass<*> -> classifier.simpleName ?: "unknown"
            else -> type.toString()
        }
    }
}
