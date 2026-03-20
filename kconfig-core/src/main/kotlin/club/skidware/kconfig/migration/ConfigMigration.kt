package club.skidware.kconfig.migration

/**
 * Defines a single-step configuration migration from one version to the next.
 *
 * Implementations transform a raw YAML map in-place (or return a new map) to
 * upgrade the configuration schema from [fromVersion] to [toVersion]. Migrations
 * are registered with [MigrationRunner] and executed in chain order during
 * [YamlConfigManager.load].
 *
 * Each migration should handle exactly one version increment (e.g., v1 to v2).
 * The [MigrationRunner] chains multiple migrations together automatically.
 *
 * Example -- renaming a property from v1 to v2:
 * ```kotlin
 * object MigrateV1ToV2 : ConfigMigration {
 *     override val fromVersion = 1
 *     override val toVersion = 2
 *
 *     override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
 *         // Rename "serverPort" to "port"
 *         map["port"] = map.remove("serverPort")
 *         // Add a new field with a default
 *         map.putIfAbsent("maxConnections", 100)
 *         return map
 *     }
 * }
 *
 * // Register the migration
 * YamlConfigManager.registerMigration(MyConfig::class, MigrateV1ToV2)
 * ```
 *
 * @see MigrationRunner
 * @see club.skidware.kconfig.YamlConfigManager.registerMigration
 * @since 1.0
 */
interface ConfigMigration {

    /**
     * The schema version this migration upgrades from.
     *
     * @since 1.0
     */
    val fromVersion: Int

    /**
     * The schema version this migration upgrades to.
     * Typically `fromVersion + 1`.
     *
     * @since 1.0
     */
    val toVersion: Int

    /**
     * Transforms the raw configuration [map] from [fromVersion] to [toVersion].
     *
     * Implementations may modify the map in-place or return a new map.
     * The `configVersion` key is automatically updated by [MigrationRunner]
     * after all migrations complete, so implementations should not set it.
     *
     * @param map The mutable raw YAML map representing the configuration at [fromVersion].
     * @return The transformed map at [toVersion].
     * @see MigrationRunner.migrate
     * @since 1.0
     */
    fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?>
}
