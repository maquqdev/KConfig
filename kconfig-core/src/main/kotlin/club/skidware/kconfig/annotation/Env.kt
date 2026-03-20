package club.skidware.kconfig.annotation

/**
 * Binds a configuration field to an environment variable, allowing its value to be
 * overridden at runtime without modifying the YAML file.
 *
 * When the specified environment variable is set, its value takes precedence over
 * the value defined in the configuration file. Use this for deployment-specific
 * settings such as credentials, hostnames, or feature flags.
 *
 * Usage:
 * ```kotlin
 * data class DatabaseConfig(
 *     @Env("DB_HOST")
 *     val host: String = "localhost",
 *
 *     @Env("DB_PORT")
 *     val port: Int = 5432,
 *
 *     @Env("DB_PASSWORD")
 *     @Secret
 *     val password: String = ""
 * )
 * ```
 *
 * @property variable The name of the environment variable to read (e.g., `"DB_HOST"`).
 * @see Secret
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Env(val variable: String)
