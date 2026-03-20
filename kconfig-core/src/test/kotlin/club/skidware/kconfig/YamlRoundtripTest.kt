package club.skidware.kconfig

import club.skidware.kconfig.annotation.Comment
import club.skidware.kconfig.annotation.CommentPlacement
import club.skidware.kconfig.serializer.BuiltinSerializers
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SecretStringSerializer
import club.skidware.kconfig.serializer.SerializerRegistry
import club.skidware.kconfig.serializer.TypeSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---- Roundtrip test data classes ----

data class BasicConfig(
    val name: String = "default-app",
    val port: Int = 8080,
    val debug: Boolean = false,
    val ratio: Double = 0.75
)

data class RtInner(val host: String = "localhost", val port: Int = 3306)
data class RtNested(val database: RtInner = RtInner(), val appName: String = "myapp")

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class CollectionConfig(
    val tags: List<String> = listOf("web", "api"),
    val settings: Map<String, Int> = mapOf("timeout" to 30, "retries" to 3),
    val level: LogLevel = LogLevel.INFO
)

data class CommentedConfig(
    @Comment("The application name")
    val name: String = "my-app",
    @Comment("Server port", placement = CommentPlacement.INLINE)
    val port: Int = 9090,
    @Comment("Enable debug mode", "Use with caution in production")
    val debug: Boolean = false
)

data class CustomType(val raw: String) {
    companion object {
        fun parse(s: String) = CustomType(s.uppercase())
    }
}

data class CustomTypeConfig(
    val custom: CustomType = CustomType("DEFAULT")
)

class YamlRoundtripTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        // Reset registry to clean state for each test.
        // YamlConfigManager is a singleton, so we ensure builtin serializers are registered.
    }

    @Nested
    inner class DefaultRoundtrip {

        @Test
        fun `save data class with defaults then load back equals original`() {
            val file = tempDir.resolve("basic.yml").toFile()
            val original = BasicConfig()

            YamlConfigManager.save(file, original)
            val loaded = YamlConfigManager.load<BasicConfig>(file)

            assertEquals(original, loaded)
        }

        @Test
        fun `save with custom values then load back preserves values`() {
            val file = tempDir.resolve("custom.yml").toFile()
            val original = BasicConfig(name = "prod", port = 443, debug = true, ratio = 1.5)

            YamlConfigManager.save(file, original)
            val loaded = YamlConfigManager.load<BasicConfig>(file)

            assertEquals(original, loaded)
        }
    }

    @Nested
    inner class FileCreation {

        @Test
        fun `file does not exist - created with defaults and loaded correctly`() {
            val file = tempDir.resolve("nonexistent.yml").toFile()

            assertTrue(!file.exists())
            val loaded = YamlConfigManager.load<BasicConfig>(file)

            assertTrue(file.exists())
            assertEquals(BasicConfig(), loaded)
        }
    }

    @Nested
    inner class NestedRoundtrip {

        @Test
        fun `nested data class roundtrips correctly`() {
            val file = tempDir.resolve("nested.yml").toFile()
            val original = RtNested(
                database = RtInner(host = "db.example.com", port = 5432),
                appName = "production"
            )

            YamlConfigManager.save(file, original)
            val loaded = YamlConfigManager.load<RtNested>(file)

            assertEquals(original, loaded)
        }
    }

    @Nested
    inner class CollectionRoundtrip {

        @Test
        fun `data class with lists and maps roundtrips`() {
            val file = tempDir.resolve("collections.yml").toFile()
            val original = CollectionConfig(
                tags = listOf("service", "backend", "v2"),
                settings = mapOf("maxConn" to 100, "idleTimeout" to 60),
                level = LogLevel.WARN
            )

            YamlConfigManager.save(file, original)
            val loaded = YamlConfigManager.load<CollectionConfig>(file)

            assertEquals(original, loaded)
        }
    }

    @Nested
    inner class CommentsInFile {

        @Test
        fun `data class with comments - comments present in YAML file`() {
            val file = tempDir.resolve("commented.yml").toFile()
            val config = CommentedConfig()

            YamlConfigManager.save(file, config)

            val content = file.readText()
            assertTrue(content.contains("# The application name"), "ABOVE comment should be present")
            assertTrue(content.contains("# Server port"), "INLINE comment should be present")
            assertTrue(content.contains("# Enable debug mode"), "Multi-line comment first line")
            assertTrue(content.contains("# Use with caution in production"), "Multi-line comment second line")

            // Also verify it loads back correctly
            val loaded = YamlConfigManager.load<CommentedConfig>(file)
            assertEquals(config, loaded)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `load file with extra keys - data loads correctly`() {
            val file = tempDir.resolve("extra.yml").toFile()
            // Write a YAML file with an extra key
            file.writeText("""
                name: loaded
                port: 3000
                debug: true
                ratio: 0.5
                unknownKey: surprise
            """.trimIndent())

            // Suppress stderr output during test
            val devNull = java.io.PrintStream(java.io.OutputStream.nullOutputStream())
            YamlConfigManager.setOutput(devNull)

            val loaded = YamlConfigManager.load<BasicConfig>(file)

            assertEquals("loaded", loaded.name)
            assertEquals(3000, loaded.port)
            assertEquals(true, loaded.debug)
            assertEquals(0.5, loaded.ratio)

            // Restore
            YamlConfigManager.setOutput(System.err)
        }

        @Test
        fun `load file with wrong types - defaults used where possible`() {
            val file = tempDir.resolve("wrongtype.yml").toFile()
            file.writeText("""
                name: ok
                port: not-a-number
                debug: false
                ratio: 1.0
            """.trimIndent())

            val devNull = java.io.PrintStream(java.io.OutputStream.nullOutputStream())
            YamlConfigManager.setOutput(devNull)

            val loaded = YamlConfigManager.load<BasicConfig>(file)

            // name should load fine
            assertEquals("ok", loaded.name)
            // port had invalid value, should fall back to default
            assertEquals(8080, loaded.port)

            YamlConfigManager.setOutput(System.err)
        }
    }

    @Nested
    inner class CustomSerializer {

        @Test
        fun `custom TypeSerializer registration and usage in roundtrip`() {
            val serializer = object : TypeSerializer<CustomType> {
                override fun serialize(value: CustomType): Any = value.raw
                override fun deserialize(raw: Any): CustomType = CustomType(raw.toString())
            }

            YamlConfigManager.registerSerializer(CustomType::class, serializer)

            val file = tempDir.resolve("custom-type.yml").toFile()
            val original = CustomTypeConfig(custom = CustomType("HELLO"))

            YamlConfigManager.save(file, original)
            val loaded = YamlConfigManager.load<CustomTypeConfig>(file)

            assertEquals("HELLO", loaded.custom.raw)
        }
    }
}
