package club.skidware.kconfig.example.config

import club.skidware.kconfig.annotation.*
import club.skidware.kconfig.serializer.SecretString

data class DatabaseConfig(
    @Comment("Database host address")
    @Env("DUELS_DB_HOST")
    val host: String = "localhost",

    @Comment("Database port")
    @Range(min = 1.0, max = 65535.0)
    val port: Int = 3306,

    @Comment("Database name")
    val database: String = "duels",

    @Comment("Database username")
    @Env("DUELS_DB_USER")
    val username: String = "duels_app",

    @Comment("Database password - never logged, never shown in debug output")
    @Secret
    @Env("DUELS_DB_PASSWORD")
    val password: SecretString = SecretString(""),

    @Comment("Maximum connection pool size")
    @Range(min = 1.0, max = 100.0)
    val maxPoolSize: Int = 10,

    @Comment("Connection timeout in milliseconds")
    @Range(min = 1000.0, max = 60000.0)
    val connectionTimeoutMs: Long = 5000
)
