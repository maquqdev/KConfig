package club.skidware.kconfig.serializer

/**
 * Core interface for converting configuration values between their typed
 * representation and a raw storage form.
 *
 * Implement this interface to support custom types in KConfig. Every
 * implementation must be able to round-trip: calling [deserialize] on the
 * output of [serialize] must produce an equivalent value.
 *
 * Example -- a custom serializer for [java.time.Instant]:
 * ```kotlin
 * object InstantSerializer : TypeSerializer<Instant> {
 *     override fun serialize(value: Instant): Any = value.toString()
 *     override fun deserialize(raw: Any): Instant = Instant.parse(raw.toString())
 * }
 *
 * // Register it:
 * registry.register(Instant::class, InstantSerializer)
 * ```
 *
 * @param T The typed value this serializer handles.
 * @see SerializerRegistry
 * @see BuiltinSerializers
 * @since 1.0
 */
interface TypeSerializer<T> {

    /**
     * Converts a typed [value] into a raw form suitable for storage (e.g. a
     * string, number, or map).
     *
     * @param value The typed value to serialize.
     * @return A raw representation of the value.
     */
    fun serialize(value: T): Any

    /**
     * Converts a [raw] storage value back into the typed representation.
     *
     * Implementations should handle reasonable type coercions (for example,
     * accepting both [Number] and [String] when the target type is numeric)
     * and throw [IllegalArgumentException] when the conversion is impossible.
     *
     * @param raw The raw value read from the configuration store.
     * @return The deserialized typed value.
     * @throws IllegalArgumentException if [raw] cannot be converted to [T].
     */
    fun deserialize(raw: Any): T
}
