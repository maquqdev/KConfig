package club.skidware.kconfig.annotation

/**
 * Excludes a configuration field from serialization and deserialization.
 *
 * Fields annotated with `@Transient` are ignored when reading from or writing to
 * the YAML configuration file. Use this for computed properties, internal state,
 * or fields that should only exist in memory.
 *
 * Usage:
 * ```kotlin
 * data class AppConfig(
 *     val name: String = "my-app",
 *
 *     @Transient
 *     val startedAt: Long = System.currentTimeMillis(),
 *
 *     @Transient
 *     val isDebugBuild: Boolean = false
 * )
 * ```
 *
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Transient
