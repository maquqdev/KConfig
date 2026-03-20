package club.skidware.kconfig.serializer

/**
 * [TypeSerializer] for [SecretString] values.
 *
 * **Serialization:** calls [SecretString.expose] to obtain the plaintext for
 * storage. This is the only place in the serialization pipeline where the
 * secret is intentionally unwrapped.
 *
 * **Deserialization:** wraps the raw value (converted via `toString()`) in a
 * new [SecretString], re-establishing the safety guarantees.
 *
 * Example:
 * ```kotlin
 * val serializer = SecretStringSerializer
 *
 * val secret = SecretString("my-api-key")
 * val raw    = serializer.serialize(secret)      // "my-api-key"
 * val back   = serializer.deserialize(raw)       // SecretString("my-api-key")
 * println(back)                                  // "********"
 * ```
 *
 * @see SecretString
 * @see TypeSerializer
 * @since 1.0
 */
object SecretStringSerializer : TypeSerializer<SecretString> {

    /**
     * Serializes a [SecretString] by exposing its plaintext value.
     *
     * @param value The [SecretString] to serialize.
     * @return The plaintext string for storage.
     */
    override fun serialize(value: SecretString): Any = value.expose()

    /**
     * Deserializes a raw value into a [SecretString].
     *
     * @param raw The raw value from the configuration store.
     * @return A new [SecretString] wrapping `raw.toString()`.
     */
    override fun deserialize(raw: Any): SecretString = SecretString(raw.toString())
}
