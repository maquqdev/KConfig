package club.skidware.kconfig.example.config

import club.skidware.kconfig.annotation.*

data class MessagesConfig(
    @Comment("Prefix shown before all plugin messages")
    val prefix: String = "&6[Duels] &f",

    @Comment("Message when a duel starts")
    @MigrateFrom("duel_start_msg")
    val duelStart: String = "&aDuel starting in %countdown% seconds!",

    @Comment("Message when a player wins")
    @MigrateFrom("win_msg")
    val victory: String = "&6%winner% &fhas defeated &c%loser%&f!",

    @Comment("Message when a duel times out")
    val timeout: String = "&7The duel ended in a draw.",

    @Comment("Message when player joins queue")
    val queueJoin: String = "&aYou joined the %mode% queue. Waiting for opponents...",

    @Comment("Message when player lacks permission")
    val noPermission: String = "&cYou don't have permission to do that.",

    @Comment("Message when player is on cooldown")
    val onCooldown: String = "&cPlease wait %remaining% seconds before using this command again."
)
