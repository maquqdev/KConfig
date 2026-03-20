package club.skidware.kconfig.error

/**
 * Mutable accumulator for [ConfigError] instances generated during deserialization and validation.
 *
 * A single collector is threaded through the entire [Deserializer][club.skidware.kconfig.reader.Deserializer]
 * call tree so that errors from nested data classes, collections, and annotation validators are
 * gathered into one place. After deserialization completes, the caller inspects the collector to
 * decide whether to use the (potentially partial) result or to abort.
 *
 * Typical usage:
 * ```kotlin
 * val errors = ConfigErrorCollector()
 * val config = deserializer.deserialize(MyConfig::class, yamlMap, errors)
 *
 * if (errors.hasErrors()) {
 *     println(ConfigErrorFormatter().format(errors.all(), "my-service"))
 * } else {
 *     startService(config!!)
 * }
 * ```
 *
 * The collector is **not** thread-safe; it is intended to be used within a single
 * deserialization pass.
 *
 * @see ConfigError
 * @see ConfigErrorFormatter
 * @see club.skidware.kconfig.reader.Deserializer
 * @since 1.0
 */
class ConfigErrorCollector {
    private val errors = mutableListOf<ConfigError>()

    /**
     * Appends a [ConfigError] to this collector.
     *
     * @param error The error to record.
     */
    fun add(error: ConfigError) {
        this.errors.add(error)
    }

    /**
     * Returns `true` if at least one error has been recorded.
     *
     * @return Whether this collector contains any errors.
     */
    fun hasErrors(): Boolean = this.errors.isNotEmpty()

    /**
     * Returns a snapshot (defensive copy) of all recorded errors.
     *
     * The returned list is independent of this collector; subsequent calls to [add] will
     * not modify it.
     *
     * @return An immutable list of all errors collected so far.
     */
    fun all(): List<ConfigError> = this.errors.toList()

    /**
     * Removes all previously recorded errors, resetting this collector to its initial state.
     *
     * Useful when re-using a collector across multiple deserialization passes.
     */
    fun clear() = this.errors.clear()

    /**
     * The number of errors currently recorded.
     */
    val size: Int get() = this.errors.size
}
