package club.skidware.kconfig.serializer

/**
 * An inline value class that wraps a sensitive string, ensuring it is never
 * accidentally leaked through [toString], logging, or string interpolation.
 *
 * **Safety guarantees:**
 * - [toString] always returns `"********"` -- the plaintext is never exposed.
 * - The only way to obtain the underlying value is by calling [expose]
 *   explicitly, making accidental leaks easy to spot in code review.
 * - Because this is an inline `value class`, there is zero runtime allocation
 *   overhead compared to a raw [String].
 *
 * Example:
 * ```kotlin
 * val apiKey = SecretString("sk-live-abc123")
 *
 * println(apiKey)            // prints: ********
 * println("Key=$apiKey")     // prints: Key=********
 * println(apiKey.expose())   // prints: sk-live-abc123  (intentional access)
 * ```
 *
 * Fields of type [SecretString] are automatically detected by
 * [SecretExtractor] and masked with [club.skidware.kconfig.annotation.MaskStrategy.FULL]
 * even without a [@Secret][club.skidware.kconfig.annotation.Secret] annotation.
 *
 * @property value The underlying plaintext string (private).
 * @see SecretStringSerializer
 * @see SecretMasker
 * @see SecretExtractor
 * @since 1.0
 */
@JvmInline
value class SecretString(private val value: String) {

    /**
     * Returns the underlying plaintext value.
     *
     * Use this method only when you intentionally need the secret in
     * plaintext -- for example, when passing it to an HTTP client header.
     *
     * @return The plaintext secret string.
     */
    fun expose(): String = value

    /**
     * Returns a fixed mask (`"********"`) to prevent accidental leakage
     * through logging, debugging, or string interpolation.
     *
     * @return The constant string `"********"`.
     */
    override fun toString(): String = "********"
}
