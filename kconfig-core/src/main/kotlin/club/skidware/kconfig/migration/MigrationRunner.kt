package club.skidware.kconfig.migration

import java.io.File

/**
 * Executes an ordered chain of [ConfigMigration] steps to upgrade a raw YAML map
 * from an older schema version to the current version.
 *
 * The runner maintains a sorted list of migrations and applies them sequentially,
 * starting from the file's detected version (read from the `configVersion` key,
 * defaulting to `1`) and ending at the target version.
 *
 * **Chaining algorithm:**
 * 1. Read `configVersion` from the raw map (defaults to `1` if absent).
 * 2. If the file version is already at or above the target, return immediately.
 * 3. Create a backup of the source file as `<filename>.v<version>.bak` (only once per version).
 * 4. Find the migration where `fromVersion == currentVersion`, apply it, advance to `toVersion`.
 * 5. Repeat step 4 until the target version is reached.
 * 6. Set `configVersion` in the map to the target version.
 *
 * If a gap exists in the migration chain (no migration for a given version),
 * an [IllegalStateException] is thrown listing the available migrations.
 *
 * Example:
 * ```kotlin
 * val runner = MigrationRunner()
 * runner.register(MigrateV1ToV2)
 * runner.register(MigrateV2ToV3)
 *
 * // Migrates from v1 -> v2 -> v3, creating config.yml.v1.bak
 * val upgraded = runner.migrate(rawMap, targetVersion = 3, sourceFile = configFile)
 * ```
 *
 * @see ConfigMigration
 * @see club.skidware.kconfig.YamlConfigManager.registerMigration
 * @since 1.0
 */
class MigrationRunner {
    private val migrations = mutableListOf<ConfigMigration>()

    /**
     * Registers a [migration] step with this runner.
     *
     * Migrations are automatically sorted by [ConfigMigration.fromVersion] after
     * each registration to ensure correct chaining order.
     *
     * @param migration The migration to register.
     * @see ConfigMigration
     * @since 1.0
     */
    fun register(migration: ConfigMigration) {
        this.migrations.add(migration)
        this.migrations.sortBy { it.fromVersion }
    }

    /**
     * Migrates a raw configuration map from its detected version to [currentVersion].
     *
     * The file version is read from the `configVersion` entry in [rawMap],
     * defaulting to `1` if absent. If the file is already at or above
     * [currentVersion], the map is returned unmodified.
     *
     * Before any transformations, a backup of [sourceFile] is created as
     * `<filename>.v<fileVersion>.bak` if the backup does not already exist.
     *
     * @param rawMap The mutable raw YAML map to migrate.
     * @param currentVersion The target schema version to migrate to.
     * @param sourceFile Optional source file for creating backups. If `null`, no backup is made.
     * @return The migrated map with `configVersion` set to [currentVersion].
     * @throws IllegalStateException If the migration chain has a gap (no migration
     *   found for a required version step).
     * @see ConfigMigration
     * @since 1.0
     */
    fun migrate(
        rawMap: MutableMap<String, Any?>,
        currentVersion: Int,
        sourceFile: File? = null
    ): MutableMap<String, Any?> {
        val fileVersion = (rawMap["configVersion"] as? Number)?.toInt() ?: 1
        if (fileVersion >= currentVersion) return rawMap

        if (sourceFile != null && sourceFile.exists()) {
            val backupFile = File("${sourceFile.absolutePath}.v${fileVersion}.bak")
            if (!backupFile.exists()) {
                sourceFile.copyTo(backupFile)
            }
        }

        var result = rawMap
        var version = fileVersion

        while (version < currentVersion) {
            val migration = this.migrations.find { it.fromVersion == version }
                ?: throw IllegalStateException(
                    "No migration found from version $version to ${version + 1}. " +
                    "Available migrations: ${this.migrations.map { "${it.fromVersion}\u2192${it.toVersion}" }}"
                )
            result = migration.migrate(result)
            version = migration.toVersion
        }

        result["configVersion"] = currentVersion
        return result
    }

    /**
     * Returns `true` if at least one migration has been registered.
     *
     * @return Whether any migrations are registered with this runner.
     * @since 1.0
     */
    fun hasMigrations(): Boolean = this.migrations.isNotEmpty()

    /**
     * Removes all registered migrations from this runner.
     *
     * @since 1.0
     */
    fun clear() = this.migrations.clear()
}
