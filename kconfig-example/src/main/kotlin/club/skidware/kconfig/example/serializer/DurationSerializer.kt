package club.skidware.kconfig.example.serializer

import club.skidware.kconfig.serializer.TypeSerializer
import java.time.Duration

/**
 * Custom serializer that stores [Duration] as a human-readable string in YAML.
 *
 * Serialized format: "5m", "2h30m", "1d12h", "30s"
 *
 * This demonstrates how to implement [TypeSerializer] for custom types
 * that aren't supported by KConfig out of the box.
 */
object DurationSerializer : TypeSerializer<Duration> {

    override fun serialize(value: Duration): Any {
        val seconds = value.seconds
        return when {
            seconds % 86400 == 0L -> "${seconds / 86400}d"
            seconds % 3600 == 0L -> "${seconds / 3600}h"
            seconds % 60 == 0L -> "${seconds / 60}m"
            else -> "${seconds}s"
        }
    }

    override fun deserialize(raw: Any): Duration {
        val str = raw.toString().trim().lowercase()
        val number = str.dropLast(1).toLongOrNull()
            ?: throw IllegalArgumentException("Invalid duration format: '$str'")

        return when (str.last()) {
            'd' -> Duration.ofDays(number)
            'h' -> Duration.ofHours(number)
            'm' -> Duration.ofMinutes(number)
            's' -> Duration.ofSeconds(number)
            else -> throw IllegalArgumentException("Unknown duration unit: '${str.last()}'. Use d, h, m, or s.")
        }
    }
}
