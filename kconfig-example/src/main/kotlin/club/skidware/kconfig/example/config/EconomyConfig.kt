package club.skidware.kconfig.example.config

import club.skidware.kconfig.annotation.*

data class EconomyConfig(
    @Comment("Reward for winning a duel")
    @Range(min = 0.0, max = 100_000.0)
    @MigrateFrom("winReward", "reward_win")
    val victoryReward: Double = 100.0,

    @Comment("Entry fee for ranked duels")
    @Range(min = 0.0, max = 10_000.0)
    val rankedEntryFee: Double = 50.0,

    @Comment("Kill streak bonus multiplier")
    @Range(min = 1.0, max = 5.0)
    val streakMultiplier: Double = 1.5,

    @Comment("Currency symbol for display")
    val currencySymbol: String = "$",

    @Comment("Enable economy integration")
    val enabled: Boolean = true
)
