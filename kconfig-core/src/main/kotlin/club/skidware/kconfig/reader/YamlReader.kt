package club.skidware.kconfig.reader

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Thin wrapper around [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) that reads
 * YAML content into a `Map<String, Any?>` suitable for consumption by the
 * [Deserializer].
 *
 * SnakeYAML automatically resolves YAML scalars to their natural JVM types (`Int`, `Double`,
 * `Boolean`, `String`, etc.) and nested mappings to `LinkedHashMap` instances. This object
 * re-uses a single [Yaml] parser instance, which is safe because SnakeYAML parsers are
 * stateless after construction.
 *
 * Usage from a file:
 * ```kotlin
 * val map = YamlReader.read(File("config.yml"))
 * val config = deserializer.deserialize(MyConfig::class, map, errors)
 * ```
 *
 * Usage from a raw string (useful in tests):
 * ```kotlin
 * val yaml = """
 *   server:
 *     port: 8080
 * """.trimIndent()
 * val map = YamlReader.readString(yaml)
 * ```
 *
 * @see Deserializer
 * @since 1.0
 */
object YamlReader {
    private val yamlThreadLocal = ThreadLocal.withInitial { Yaml() }

    /**
     * Reads and parses a YAML file into a flat/nested map.
     *
     * If the file does not exist, an empty map is returned rather than throwing an exception.
     * The file is read using UTF-8 encoding and the underlying [java.io.Reader] is closed
     * automatically after parsing.
     *
     * This method is thread-safe - each thread uses its own [Yaml] parser instance.
     *
     * @param file The YAML file to read.
     * @return A map representing the top-level YAML mapping, or an empty map if the file
     *         does not exist or contains no mappings.
     */
    @Suppress("UNCHECKED_CAST")
    fun read(file: File): Map<String, Any?> {
        if (!file.exists()) return emptyMap()
        return file.reader(Charsets.UTF_8).use { this.yamlThreadLocal.get().load(it) as? Map<String, Any?> } ?: emptyMap()
    }

    /**
     * Parses a YAML string directly into a flat/nested map.
     *
     * Primarily useful for unit tests and scenarios where the YAML content is already
     * available in memory (e.g. fetched from a remote source).
     *
     * This method is thread-safe - each thread uses its own [Yaml] parser instance.
     *
     * @param content The raw YAML string to parse.
     * @return A map representing the top-level YAML mapping, or an empty map if the
     *         content is empty or does not contain a mapping.
     */
    @Suppress("UNCHECKED_CAST")
    fun readString(content: String): Map<String, Any?> {
        return this.yamlThreadLocal.get().load(content) as? Map<String, Any?> ?: emptyMap()
    }
}
