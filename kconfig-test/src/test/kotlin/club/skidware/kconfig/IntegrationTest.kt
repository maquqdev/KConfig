package club.skidware.kconfig

import club.skidware.kconfig.annotation.*
import club.skidware.kconfig.annotation.Transient
import club.skidware.kconfig.error.ConfigError
import club.skidware.kconfig.error.ConfigErrorCollector
import club.skidware.kconfig.error.ConfigErrorFormatter
import club.skidware.kconfig.migration.ConfigMigration
import club.skidware.kconfig.reader.Deserializer
import club.skidware.kconfig.reader.YamlReader
import club.skidware.kconfig.serializer.BuiltinSerializers
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SecretStringSerializer
import club.skidware.kconfig.serializer.SerializerRegistry
import club.skidware.kconfig.writer.CommentExtractor
import club.skidware.kconfig.writer.Serializer
import club.skidware.kconfig.writer.YamlWriter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class DatabaseConfig(
    @Comment("Database server hostname")
    val host: String = "localhost",

    @Comment("Database port number", placement = CommentPlacement.INLINE)
    @Range(min = 1.0, max = 65535.0)
    val port: Int = 5432,

    @Comment("Database username")
    val username: String = "app",

    @Comment("Database password (fully masked in debug output)")
    @Secret(mask = MaskStrategy.FULL)
    val password: String = "",

    @Comment("JDBC connection URL, overridable via DB_URL env var")
    @Env("DB_URL")
    val url: String = "jdbc:postgresql://localhost:5432/mydb"
)

data class ServerConfig(
    @Comment("HTTP port the server listens on")
    @Range(min = 1.0, max = 65535.0)
    val port: Int = 8080,

    @Comment("Server hostname (lowercase letters, digits, and dashes only)")
    @Pattern(
        regex = "^[a-z][a-z0-9-]*$",
        description = "lowercase with optional digits and dashes"
    )
    val hostname: String = "app-server",

    @Comment("Network bind address")
    val bindAddress: String = "0.0.0.0"
)

data class LoggingConfig(
    @Comment("Logging level")
    val level: LogLevel = LogLevel.INFO,

    @Transient
    val runtimeLogFile: String = "/tmp/app.log"
)

data class CacheConfig(
    @Comment("Whether the cache is enabled")
    val enabled: Boolean = true,

    @Comment("Time-to-live in seconds")
    val ttlSeconds: Int = 120,

    @Comment("Maximum number of cache entries")
    @Range(min = 1.0, max = 100_000.0)
    val maxEntries: Int = 10000
)

data class AppConfig(
    @Comment("Human-readable application name")
    val appName: String = "my-app",

    @Comment("Database settings")
    val database: DatabaseConfig = DatabaseConfig(),

    @Comment("HTTP server settings")
    val server: ServerConfig = ServerConfig(),

    @Comment("Logging configuration")
    val logging: LoggingConfig = LoggingConfig(),

    @Comment("Cache configuration")
    val cache: CacheConfig = CacheConfig(),

    @Comment(
        "Allowed CORS origins",
        "Add each allowed origin as a list entry"
    )
    @MigrateFrom("allowedHosts")
    val allowedOrigins: List<String> = listOf("http://localhost"),

    @Comment("Per-endpoint rate limits (requests per minute)")
    val rateLimits: Map<String, Int> = mapOf("api" to 60),

    @Comment("API key for external service (partially masked in debug output)")
    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 7)
    val apiKey: String = "",

    val configVersion: Int = 2,

    @Transient
    val runtimeStartedAt: Long = System.currentTimeMillis()
)


class IntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class FullRoundtrip {

        @Test
        fun `save and reload a realistic config with all features`() {
            val file = tempDir.resolve("config.yml").toFile()
            val original = AppConfig(
                appName = "production-service",
                database = DatabaseConfig(
                    host = "db.prod.internal",
                    port = 5432,
                    username = "prod_user",
                    password = "correct-horse-battery-staple",
                    url = "jdbc:postgresql://db.prod.internal:5432/proddb"
                ),
                server = ServerConfig(
                    port = 9090,
                    hostname = "web-prod-01",
                    bindAddress = "0.0.0.0"
                ),
                logging = LoggingConfig(level = LogLevel.WARN),
                cache = CacheConfig(enabled = true, ttlSeconds = 600, maxEntries = 50000),
                allowedOrigins = listOf("https://example.com", "https://app.example.com"),
                rateLimits = mapOf("login" to 10, "api" to 100, "health" to 1000),
                apiKey = "sk-live-abcdef1234567890"
            )

            YamlConfigManager.save(file, original)

            assertTrue(file.exists())
            val content = file.readText()

            assertTrue(content.contains("# Human-readable application name"))
            assertTrue(content.contains("# Database server hostname"))
            assertTrue(content.contains("# HTTP port the server listens on"))
            assertTrue(content.contains("# Logging level"))
            assertTrue(content.contains("# Maximum number of cache entries"))
            assertTrue(content.contains("# Allowed CORS origins"))
            assertTrue(content.contains("# Add each allowed origin as a list entry"))
            assertTrue(content.contains("# Per-endpoint rate limits (requests per minute)"))
            assertTrue(content.contains("# API key for external service (partially masked in debug output)"))

            assertTrue(!content.contains("runtimeLogFile"))
            assertTrue(!content.contains("runtimeStartedAt"))

            val loaded = YamlConfigManager.load<AppConfig>(file)
            assertEquals(original.appName, loaded.appName)
            assertEquals(original.database.host, loaded.database.host)
            assertEquals(original.database.port, loaded.database.port)
            assertEquals(original.database.username, loaded.database.username)
            assertEquals(original.database.password, loaded.database.password)
            assertEquals(original.database.url, loaded.database.url)
            assertEquals(original.server.port, loaded.server.port)
            assertEquals(original.server.hostname, loaded.server.hostname)
            assertEquals(original.server.bindAddress, loaded.server.bindAddress)
            assertEquals(original.logging.level, loaded.logging.level)
            assertEquals(original.cache.enabled, loaded.cache.enabled)
            assertEquals(original.cache.ttlSeconds, loaded.cache.ttlSeconds)
            assertEquals(original.cache.maxEntries, loaded.cache.maxEntries)
            assertEquals(original.allowedOrigins, loaded.allowedOrigins)
            assertEquals(original.rateLimits, loaded.rateLimits)
            assertEquals(original.apiKey, loaded.apiKey)

            assertEquals("/tmp/app.log", loaded.logging.runtimeLogFile)
        }
    }

    @Nested
    inner class LoadFromResource {

        @Test
        fun `load config from test resource file`() {
            val resourceContent = javaClass.classLoader.getResourceAsStream("app-config.yml")!!
                .bufferedReader().readText()
            val file = tempDir.resolve("app.yml").toFile()
            file.writeText(resourceContent)

            val devNull = PrintStream(OutputStream.nullOutputStream())
            YamlConfigManager.setOutput(devNull)

            val config = YamlConfigManager.load<AppConfig>(file)

            assertEquals("my-web-service", config.appName)
            assertEquals("db.production.internal", config.database.host)
            assertEquals(5432, config.database.port)
            assertEquals("app_user", config.database.username)
            assertEquals("correct-horse-battery-staple", config.database.password)
            assertEquals(8080, config.server.port)
            assertEquals("web-prod-01", config.server.hostname)
            assertEquals(LogLevel.INFO, config.logging.level)
            assertEquals(true, config.cache.enabled)
            assertEquals(300, config.cache.ttlSeconds)
            assertEquals(5000, config.cache.maxEntries)
            assertEquals(3, config.allowedOrigins.size)
            assertTrue(config.allowedOrigins.contains("https://admin.example.com"))
            assertEquals(10, config.rateLimits["login"])
            assertEquals(100, config.rateLimits["api"])
            assertEquals(1000, config.rateLimits["health"])

            YamlConfigManager.setOutput(System.err)
        }
    }

    @Nested
    inner class BrokenConfig {

        @Test
        fun `broken config loads with fallbacks and reports errors`() {
            val resourceContent = javaClass.classLoader.getResourceAsStream("broken-config.yml")!!
                .bufferedReader().readText()

            val registry = SerializerRegistry()
            BuiltinSerializers.registerAll(registry)
            registry.register(SecretString::class, SecretStringSerializer)
            val deserializer = Deserializer(registry)
            val errors = ConfigErrorCollector()

            val rawMap = YamlReader.readString(resourceContent)
            val config = deserializer.deserialize(AppConfig::class, rawMap, errors)

            assertNotNull(config)
            assertTrue(errors.hasErrors(), "Should have errors for broken config")

            val allErrors = errors.all()
            assertTrue(allErrors.isNotEmpty())

            // server.port = -1 should violate @Range(1, 65535)
            assertTrue(
                allErrors.any { it is ConfigError.OutOfRange && it.path == "server.port" },
                "Expected OutOfRange error for server.port, got: $allErrors"
            )

            // cache.maxEntries = -500 should violate @Range(1, 100000)
            assertTrue(
                allErrors.any { it is ConfigError.OutOfRange && it.path == "cache.maxEntries" },
                "Expected OutOfRange error for cache.maxEntries, got: $allErrors"
            )

            // server.hostname = "INVALID_HOST_123!" should violate @Pattern
            assertTrue(
                allErrors.any { it is ConfigError.PatternMismatch && it.path == "server.hostname" },
                "Expected PatternMismatch error for server.hostname, got: $allErrors"
            )

            // unknownTopLevel should be an unknown key
            assertTrue(
                allErrors.any { it is ConfigError.UnknownKey && it.path == "unknownTopLevel" },
                "Expected UnknownKey error for unknownTopLevel, got: $allErrors"
            )

            // Format the errors report
            val formatter = ConfigErrorFormatter(useColors = false)
            val report = formatter.format(allErrors, "broken-config.yml")
            assertTrue(report.isNotEmpty())
            assertTrue(report.contains("config error"))
        }

        @Test
        fun `error report format is human-readable`() {
            val errors = listOf(
                ConfigError.OutOfRange("server.port", -1, 1.0, 65535.0, 8080),
                ConfigError.UnknownKey("unknownField", "unknownField"),
                ConfigError.InvalidValue("cache.enabled", "maybe", "Boolean"),
                ConfigError.PatternMismatch(
                    "server.hostname", "INVALID_HOST_123!",
                    "^[a-z][a-z0-9-]*$", "lowercase with optional digits and dashes"
                )
            )

            val formatter = ConfigErrorFormatter(useColors = false)
            val report = formatter.format(errors, "config.yml")

            assertTrue(report.contains("4 config error(s)"))
            assertTrue(report.contains("server.port"))
            assertTrue(report.contains("out of range"))
            assertTrue(report.contains("cache.enabled"))
            assertTrue(report.contains("server.hostname"))
        }
    }

    @Nested
    inner class MigrationIntegration {

        @Test
        fun `migrate v1 config with old field names to v2`() {
            val resourceContent = javaClass.classLoader.getResourceAsStream("app-config-v1.yml")!!
                .bufferedReader().readText()
            val file = tempDir.resolve("app-migrate.yml").toFile()
            file.writeText(resourceContent)

            // Register a migration that handles v1 -> v2 changes
            YamlConfigManager.registerMigration(AppConfig::class, object : ConfigMigration {
                override val fromVersion = 1
                override val toVersion = 2
                override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
                    // Rename allowedHosts -> allowedOrigins
                    map["allowedOrigins"] = map.remove("allowedHosts") ?: map["allowedOrigins"]
                    map["configVersion"] = 2
                    return map
                }
            })

            val devNull = PrintStream(OutputStream.nullOutputStream())
            YamlConfigManager.setOutput(devNull)

            val config = YamlConfigManager.load<AppConfig>(file)

            // allowedHosts should have been migrated to allowedOrigins
            assertEquals(2, config.allowedOrigins.size)
            assertTrue(config.allowedOrigins.contains("https://example.com"))
            assertTrue(config.allowedOrigins.contains("https://legacy.example.com"))

            // Other fields should load correctly after migration
            assertEquals("my-web-service", config.appName)
            assertEquals("db.staging.internal", config.database.host)
            assertEquals(9090, config.server.port)
            assertEquals(LogLevel.DEBUG, config.logging.level)
            assertEquals(false, config.cache.enabled)

            // A backup should have been created
            val backupFile = File("${file.absolutePath}.v1.bak")
            assertTrue(backupFile.exists(), "Backup file should be created before migration")

            // Clean up
            backupFile.delete()
            YamlConfigManager.setOutput(System.err)
        }
    }

    @Nested
    inner class MigrateFromAnnotation {

        @Test
        fun `MigrateFrom picks up old key when current key is absent`() {
            // Simulate a YAML map that uses the old key "allowedHosts" instead of "allowedOrigins"
            val rawMap = mapOf(
                "appName" to "test-app",
                "allowedHosts" to listOf("https://old.example.com"),
                "configVersion" to 2
            )

            val registry = SerializerRegistry()
            BuiltinSerializers.registerAll(registry)
            registry.register(SecretString::class, SecretStringSerializer)
            val deserializer = Deserializer(registry)
            val errors = ConfigErrorCollector()

            val config = deserializer.deserialize(AppConfig::class, rawMap, errors)

            assertNotNull(config)
            assertEquals(listOf("https://old.example.com"), config.allowedOrigins)
        }
    }

    @Nested
    inner class SecretMaskingIntegration {

        @Test
        fun `debug string masks secrets with different strategies`() {
            val config = AppConfig(
                appName = "debug-test",
                database = DatabaseConfig(
                    host = "localhost",
                    password = "my-super-secret-password"
                ),
                apiKey = "sk-live-abcdef1234567890"
            )

            val debugStr = YamlConfigManager.toDebugString(config)

            // appName should be visible
            assertTrue(debugStr.contains("debug-test"))

            // database.password has @Secret(FULL) -> fully masked
            assertTrue(!debugStr.contains("my-super-secret-password"),
                "FULL-masked password should NOT appear in debug output")
            assertTrue(debugStr.contains("********"),
                "Fully masked field should show asterisks")

            // apiKey has @Secret(PARTIAL, visibleChars=7) -> "sk-live********"
            assertTrue(!debugStr.contains("sk-live-abcdef1234567890"),
                "PARTIAL-masked apiKey should NOT appear in full in debug output")
            assertTrue(debugStr.contains("sk-live"),
                "PARTIAL mask should reveal the first 7 characters")
        }
    }

    @Nested
    inner class CommentExtraction {

        @Test
        fun `all comments are extracted from nested config`() {
            val comments = CommentExtractor.extract(AppConfig::class)

            // Top-level comments
            assertTrue(comments.containsKey("appName"))
            assertTrue(comments.containsKey("allowedOrigins"))
            assertTrue(comments.containsKey("rateLimits"))
            assertTrue(comments.containsKey("apiKey"))

            // DatabaseConfig comments
            assertTrue(comments.containsKey("database"))
            assertTrue(comments.containsKey("database.host"))
            assertTrue(comments.containsKey("database.port"))
            assertTrue(comments.containsKey("database.username"))
            assertTrue(comments.containsKey("database.password"))
            assertTrue(comments.containsKey("database.url"))

            // ServerConfig comments
            assertTrue(comments.containsKey("server"))
            assertTrue(comments.containsKey("server.port"))
            assertTrue(comments.containsKey("server.hostname"))
            assertTrue(comments.containsKey("server.bindAddress"))

            // LoggingConfig comments
            assertTrue(comments.containsKey("logging"))
            assertTrue(comments.containsKey("logging.level"))

            // CacheConfig comments
            assertTrue(comments.containsKey("cache"))
            assertTrue(comments.containsKey("cache.enabled"))
            assertTrue(comments.containsKey("cache.ttlSeconds"))
            assertTrue(comments.containsKey("cache.maxEntries"))

            // Verify comment content
            assertEquals(
                "Human-readable application name",
                comments["appName"]!!.lines[0]
            )
            assertEquals(
                "Database server hostname",
                comments["database.host"]!!.lines[0]
            )
            assertEquals(
                "HTTP port the server listens on",
                comments["server.port"]!!.lines[0]
            )

            // Multi-line comment on allowedOrigins
            assertEquals(2, comments["allowedOrigins"]!!.lines.size)
            assertEquals("Allowed CORS origins", comments["allowedOrigins"]!!.lines[0])
            assertEquals("Add each allowed origin as a list entry", comments["allowedOrigins"]!!.lines[1])

            // Inline comment placement on database.port
            assertEquals(
                CommentPlacement.INLINE,
                comments["database.port"]!!.placement
            )
        }
    }

    @Nested
    inner class FileAutoCreation {

        @Test
        fun `non-existent config file is created with defaults and comments`() {
            val file = tempDir.resolve("new-config.yml").toFile()

            assertTrue(!file.exists())

            val config = YamlConfigManager.load<AppConfig>(file)

            assertTrue(file.exists())
            val content = file.readText()

            // Should have default values
            assertTrue(content.contains("appName"))
            assertTrue(content.contains("my-app"))
            assertTrue(content.contains("localhost"))

            // Should have comments
            assertTrue(content.contains("# Human-readable application name"))
            assertTrue(content.contains("# Database server hostname"))
            assertTrue(content.contains("# HTTP port the server listens on"))
            assertTrue(content.contains("# Logging level"))

            // Should NOT have @Transient fields
            assertTrue(!content.contains("runtimeLogFile"))
            assertTrue(!content.contains("runtimeStartedAt"))

            // Default values should be correct
            assertEquals("my-app", config.appName)
            assertEquals("localhost", config.database.host)
            assertEquals(5432, config.database.port)
            assertEquals(8080, config.server.port)
            assertEquals(LogLevel.INFO, config.logging.level)
            assertEquals(true, config.cache.enabled)
            assertEquals(10000, config.cache.maxEntries)
            assertEquals(listOf("http://localhost"), config.allowedOrigins)
        }
    }

    @Nested
    inner class SerializerWriterPipeline {

        @Test
        fun `serialize config preserves all fields, nesting, and ordering`() {
            val config = AppConfig(
                appName = "pipeline-test",
                database = DatabaseConfig(host = "db.test", port = 3306, username = "tester"),
                server = ServerConfig(port = 4000),
                logging = LoggingConfig(level = LogLevel.DEBUG),
                cache = CacheConfig(enabled = false, maxEntries = 500),
                allowedOrigins = listOf("http://a.com", "http://b.com"),
                rateLimits = mapOf("search" to 30, "upload" to 5),
                apiKey = "sk-test-key123"
            )

            val registry = SerializerRegistry()
            BuiltinSerializers.registerAll(registry)
            registry.register(SecretString::class, SecretStringSerializer)

            val data = Serializer.serialize(config, registry)
            val yaml = YamlWriter.writeToString(data)

            // Check that serialized map has correct structure
            assertTrue(data.containsKey("appName"))
            assertTrue(data.containsKey("database"))
            assertTrue(data.containsKey("server"))
            assertTrue(data.containsKey("logging"))
            assertTrue(data.containsKey("cache"))
            assertTrue(data.containsKey("allowedOrigins"))
            assertTrue(data.containsKey("rateLimits"))
            assertTrue(data.containsKey("apiKey"))
            assertTrue(!data.containsKey("runtimeStartedAt"), "@Transient field should be omitted from top-level")

            // Nested data class serialization
            val db = data["database"] as Map<*, *>
            assertEquals("db.test", db["host"])
            assertEquals(3306, db["port"])
            assertEquals("tester", db["username"])

            val logging = data["logging"] as Map<*, *>
            assertEquals("DEBUG", logging["level"])
            assertTrue(!logging.containsKey("runtimeLogFile"), "@Transient field should be omitted from nested config")

            val cache = data["cache"] as Map<*, *>
            assertEquals(false, cache["enabled"])
            assertEquals(500, cache["maxEntries"])

            // List serialization
            val origins = data["allowedOrigins"] as List<*>
            assertEquals(2, origins.size)
            assertEquals("http://a.com", origins[0])

            // Map serialization
            val limits = data["rateLimits"] as Map<*, *>
            assertEquals(30, limits["search"])
            assertEquals(5, limits["upload"])

            // YAML output should be properly formatted
            assertTrue(yaml.contains("appName: pipeline-test"))
            assertTrue(yaml.contains("  host: db.test"))
            assertTrue(yaml.contains("  port: 3306"))
            assertTrue(yaml.contains("  level: DEBUG"))
            assertTrue(yaml.contains("  - \"http://a.com\""))
        }
    }

    @Nested
    inner class EnumHandling {

        @Test
        fun `enum values are deserialized case-insensitively`() {
            val rawMap = mapOf(
                "level" to "warn"
            )

            val registry = SerializerRegistry()
            BuiltinSerializers.registerAll(registry)
            val deserializer = Deserializer(registry)
            val errors = ConfigErrorCollector()

            val config = deserializer.deserialize(LoggingConfig::class, rawMap, errors)

            assertNotNull(config)
            assertTrue(!errors.hasErrors(), "Case-insensitive enum match should not produce errors")
            assertEquals(LogLevel.WARN, config.level)
        }

        @Test
        fun `invalid enum value reports error`() {
            val rawMap = mapOf(
                "level" to "TRACE"
            )

            val registry = SerializerRegistry()
            BuiltinSerializers.registerAll(registry)
            val deserializer = Deserializer(registry)
            val errors = ConfigErrorCollector()

            deserializer.deserialize(LoggingConfig::class, rawMap, errors)

            assertTrue(errors.hasErrors(), "Invalid enum value should produce an error")
            assertTrue(
                errors.all().any { it is ConfigError.InvalidValue && it.path == "level" },
                "Expected InvalidValue error for level"
            )
        }
    }

    @Nested
    inner class SecretStringType {

        @Test
        fun `SecretString toString never leaks the value`() {
            val secret = SecretString("my-actual-secret")

            assertEquals("********", secret.toString())
            assertEquals("********", "$secret")
            assertEquals("my-actual-secret", secret.expose())
        }
    }
}
