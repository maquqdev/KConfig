package club.skidware.kconfig.example.migration

import club.skidware.kconfig.migration.ConfigMigration

/**
 * Migrates DuelsConfig from version 1 to version 2.
 *
 * Changes in v2:
 * - Renamed `economy.winReward` → `economy.victoryReward`
 * - Renamed `messages.duel_start_msg` → `messages.duelStart`
 * - Added `commandCooldowns` map (filled with defaults if absent)
 * - Removed deprecated `legacyMode` flag
 */
object V1ToV2Migration : ConfigMigration {

    override val fromVersion = 1
    override val toVersion = 2

    @Suppress("UNCHECKED_CAST")
    override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
        val economy = (map["economy"] as? MutableMap<String, Any?>) ?: mutableMapOf()
        economy["victoryReward"] = economy.remove("winReward") ?: economy.remove("reward_win") ?: 100.0
        map["economy"] = economy

        val messages = (map["messages"] as? MutableMap<String, Any?>) ?: mutableMapOf()
        messages["duelStart"] = messages.remove("duel_start_msg") ?: messages["duelStart"]
        messages["victory"] = messages.remove("win_msg") ?: messages["victory"]
        map["messages"] = messages

        if (!map.containsKey("commandCooldowns")) {
            map["commandCooldowns"] = mapOf("duel" to 30, "stats" to 10, "spectate" to 5)
        }

        map.remove("legacyMode")

        map["configVersion"] = 2
        return map
    }
}
