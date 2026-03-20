package club.skidware.kconfig.example.config

import club.skidware.kconfig.annotation.*
import club.skidware.kconfig.serializer.SecretString

data class DuelsConfig(
    @Comment("Unique server identifier for cross-server communication")
    val serverId: String = "duels-01",

    @Comment("Database connection settings")
    val database: DatabaseConfig = DatabaseConfig(),

    @Comment("Game mechanics settings")
    val game: GameConfig = GameConfig(),

    @Comment("Economy and rewards configuration")
    val economy: EconomyConfig = EconomyConfig(),

    @Comment("Message templates")
    val messages: MessagesConfig = MessagesConfig(),

    @Comment("API key for stats service")
    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 8)
    val statsApiKey: String = "",

    @Comment("Allowed arena worlds")
    val arenaWorlds: List<String> = listOf("duels-arena-1", "duels-arena-2"),

    @Comment(
        "Per-command cooldowns in seconds",
        "Maps command name to cooldown duration"
    )
    val commandCooldowns: Map<String, Int> = mapOf(
        "duel" to 30,
        "stats" to 10,
        "spectate" to 5
    ),

    val configVersion: Int = 2,

    @Transient
    val startupTimestamp: Long = System.currentTimeMillis()
)
