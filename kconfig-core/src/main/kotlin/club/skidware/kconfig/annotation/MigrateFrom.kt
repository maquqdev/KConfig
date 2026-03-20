package club.skidware.kconfig.annotation

/**
 * Specifies one or more legacy YAML keys that should be migrated to the annotated field
 * when loading a configuration file.
 *
 * Use this annotation when renaming or reorganizing configuration fields to maintain
 * backward compatibility with existing config files. During deserialization, if the
 * current key is absent but one of the old keys is present, the value is read from
 * the old key. Multiple old keys are checked in the order provided.
 *
 * Usage:
 * ```kotlin
 * data class ServerConfig(
 *     @MigrateFrom("server-name", "serverName")
 *     val name: String = "default",
 *
 *     @MigrateFrom("max-players")
 *     val maxPlayers: Int = 100
 * )
 * ```
 *
 * @property oldKeys One or more legacy key names to check during deserialization, in priority order.
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MigrateFrom(vararg val oldKeys: String)
