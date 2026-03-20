package club.skidware.kconfig.example

import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.bukkit.BukkitSerializers
import club.skidware.kconfig.example.config.DuelsConfig
import club.skidware.kconfig.example.migration.V1ToV2Migration
import club.skidware.kconfig.example.serializer.DurationSerializer
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration

/**
 * Example Bukkit plugin demonstrating full KConfig usage.
 *
 * This shows the recommended pattern for integrating KConfig:
 * 1. Register serializers in onEnable (Bukkit types + custom types)
 * 2. Register migrations for backward compatibility
 * 3. Load config - file is auto-created with defaults if missing
 * 4. Optionally watch for live reloads
 * 5. Access config fields throughout the plugin
 * 6. Clean up watchers in onDisable
 */
class DuelsPlugin : JavaPlugin() {

    lateinit var duelsConfig: DuelsConfig
        private set

    override fun onEnable() {
        // Step 1: Register Bukkit-specific serializers (Location, Vector, Color, ItemStack)
        BukkitSerializers.registerAll(YamlConfigManager)

        // Step 2: Register custom serializers
        YamlConfigManager.registerSerializer(Duration::class, DurationSerializer)
        // SecretString is registered automatically by YamlConfigManager

        // Step 3: Register config migrations
        YamlConfigManager.registerMigration(DuelsConfig::class, V1ToV2Migration)

        // Step 4: Load the config
        // - If config.yml doesn't exist → created with all defaults and @Comment annotations
        // - If config.yml has old version → migrated automatically, backup created
        // - If config.yml has invalid values → errors logged, defaults used for invalid fields
        // - @Env fields are overridden from environment variables
        duelsConfig = YamlConfigManager.load<DuelsConfig>(dataFolder.resolve("config.yml"))

        // Step 5: Optionally enable live reload via file watcher
        YamlConfigManager.watch<DuelsConfig>(dataFolder.resolve("config.yml")) { newConfig ->
            duelsConfig = newConfig
            logger.info("Config reloaded! Server ID: ${duelsConfig.serverId}")
        }

        // Step 6: Use config values
        logger.info("Duels plugin enabled!")
        logger.info("Server ID: ${duelsConfig.serverId}")
        logger.info("Database: ${duelsConfig.database.host}:${duelsConfig.database.port}")
        logger.info("Default mode: ${duelsConfig.game.defaultMode}")
        logger.info("Victory reward: ${duelsConfig.economy.currencySymbol}${duelsConfig.economy.victoryReward}")
        logger.info("Arena worlds: ${duelsConfig.arenaWorlds.joinToString()}")

        // Safe debug logging - secrets are masked automatically
        logger.info("Debug config dump:\n${YamlConfigManager.toDebugString(duelsConfig)}")

        // SecretString never leaks in toString/interpolation
        logger.info("DB password (safe): ${duelsConfig.database.password}") // prints "********"
    }

    override fun onDisable() {
        // Clean up file watchers
        YamlConfigManager.stopAllWatchers()

        // Optionally save config (e.g., if runtime values changed)
        // YamlConfigManager.save(dataFolder.resolve("config.yml"), duelsConfig)

        logger.info("Duels plugin disabled.")
    }

    /**
     * Reload command handler - demonstrates manual reload.
     */
    fun reloadDuelsConfig() {
        duelsConfig = YamlConfigManager.load<DuelsConfig>(dataFolder.resolve("config.yml"))
        logger.info("Config manually reloaded.")
    }
}
