package club.skidware.kconfig.annotation

/**
 * Validates that a string configuration field matches the given regular expression.
 *
 * When a configuration is loaded, the field value is checked against [regex].
 * If it does not match, a validation error is raised. An optional [description]
 * can be provided to give users a human-readable explanation of the expected format.
 *
 * Usage:
 * ```kotlin
 * data class NetworkConfig(
 *     @Pattern(
 *         regex = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$",
 *         description = "Must be a valid IPv4 address"
 *     )
 *     val bindAddress: String = "0.0.0.0",
 *
 *     @Pattern(regex = "^[a-z][a-z0-9-]*$")
 *     val hostname: String = "app-server"
 * )
 * ```
 *
 * @property regex The regular expression the field value must match.
 * @property description An optional human-readable description of the expected format, used in validation error messages. Defaults to an empty string.
 * @see Range
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pattern(val regex: String, val description: String = "")
