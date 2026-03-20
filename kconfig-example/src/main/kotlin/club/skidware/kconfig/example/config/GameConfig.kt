package club.skidware.kconfig.example.config

import club.skidware.kconfig.annotation.*

enum class DuelMode {
    CLASSIC, RANKED, TOURNAMENT, PRACTICE
}

enum class Kit {
    DIAMOND, IRON, ARCHER, CUSTOM
}

data class GameConfig(
    @Comment("Default duel mode")
    val defaultMode: DuelMode = DuelMode.CLASSIC,

    @Comment("Available kits for players")
    val availableKits: List<Kit> = listOf(Kit.DIAMOND, Kit.IRON, Kit.ARCHER),

    @Comment("Countdown seconds before duel starts")
    @Range(min = 1.0, max = 30.0)
    val countdownSeconds: Int = 5,

    @Comment("Maximum duel duration in minutes")
    @Range(min = 1.0, max = 60.0)
    val maxDurationMinutes: Int = 10,

    @Comment("Minimum players required to start")
    @Range(min = 2.0, max = 4.0)
    val minPlayers: Int = 2,

    @Comment("Arena name pattern (lowercase, digits, dashes)")
    @Pattern(
        regex = "^[a-z][a-z0-9-]*$",
        description = "lowercase letters, digits, and dashes"
    )
    val arenaPrefix: String = "arena",

    @Comment("Enable spectator mode")
    val spectators: Boolean = true,

    @Comment("Enable post-match replay")
    val replays: Boolean = false,

    @Transient
    val activeGames: Int = 0
)
