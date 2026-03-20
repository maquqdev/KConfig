package club.skidware.kconfig.annotation

/**
 * Marks a configuration field as sensitive, ensuring its value is masked in logs,
 * debug output, and serialized representations.
 *
 * Use this annotation for passwords, API keys, tokens, and other credentials.
 * The masking behavior is controlled by the [mask] strategy and [visibleChars] parameter.
 *
 * Usage:
 * ```kotlin
 * data class ApiConfig(
 *     @Secret
 *     val apiKey: String = "",
 *
 *     @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 4)
 *     val token: String = "",
 *
 *     @Secret(mask = MaskStrategy.EDGES)
 *     val password: String = ""
 * )
 * ```
 *
 * @property mask The masking strategy to apply when the value is displayed. Defaults to [MaskStrategy.FULL].
 * @property visibleChars The number of characters to leave unmasked when using [MaskStrategy.PARTIAL] or [MaskStrategy.EDGES]. Defaults to `4`.
 * @see MaskStrategy
 * @see Env
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Secret(
    val mask: MaskStrategy = MaskStrategy.FULL,
    val visibleChars: Int = 4
)

/**
 * Defines how a [Secret]-annotated field value is masked when displayed in logs or debug output.
 *
 * Each strategy offers a different balance between security and debuggability:
 * - [FULL] hides the entire value, suitable for highly sensitive data.
 * - [PARTIAL] reveals a prefix, useful for identifying which key or token is in use.
 * - [EDGES] reveals the first and last characters, helpful for quick visual verification.
 *
 * @see Secret
 * @since 1.0
 */
enum class MaskStrategy {
    /** Replaces the entire value with asterisks: `"superSecret123"` becomes `"********"`. */
    FULL,
    /** Reveals the first [Secret.visibleChars] characters and masks the rest: `"superSecret123"` becomes `"supe********"`. */
    PARTIAL,
    /** Reveals the first and last characters and masks everything in between: `"superSecret123"` becomes `"s*************3"`. */
    EDGES
}
