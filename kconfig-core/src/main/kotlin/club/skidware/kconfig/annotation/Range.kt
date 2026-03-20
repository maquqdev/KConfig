package club.skidware.kconfig.annotation

/**
 * Constrains a numeric configuration field to an inclusive range between [min] and [max].
 *
 * When a configuration is loaded, the field value is validated to ensure it falls
 * within the specified bounds. Works with all numeric types (`Int`, `Long`, `Float`, `Double`).
 * If the value is outside the range, a validation error is raised.
 *
 * Usage:
 * ```kotlin
 * data class ServerConfig(
 *     @Range(min = 1.0, max = 65535.0)
 *     val port: Int = 8080,
 *
 *     @Range(min = 0.0, max = 1.0)
 *     val loadFactor: Double = 0.75,
 *
 *     @Range(min = 1.0, max = 1000.0)
 *     val maxPlayers: Int = 200
 * )
 * ```
 *
 * @property min The minimum allowed value (inclusive).
 * @property max The maximum allowed value (inclusive).
 * @see Pattern
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Range(val min: Double, val max: Double)
