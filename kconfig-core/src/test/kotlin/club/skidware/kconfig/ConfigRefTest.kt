package club.skidware.kconfig

import club.skidware.kconfig.annotation.Comment
import club.skidware.kconfig.annotation.Range
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

data class RefInner(
    val host: String = "localhost",
    val port: Int = 3306
)

data class RefConfig(
    @Comment("App name")
    val name: String = "test-app",
    @Range(min = 1.0, max = 65535.0)
    val port: Int = 8080,
    val debug: Boolean = false,
    val tags: List<String> = listOf("a", "b"),
    val database: RefInner = RefInner()
)

class ConfigRefTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    inner class BasicAccess {

        @Test
        fun `invoke operator returns current config`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            assertEquals("test-app", ref().name)
            assertEquals(8080, ref().port)
        }

        @Test
        fun `current property returns current config`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            assertEquals("test-app", ref.current.name)
            assertEquals(8080, ref.current.port)
        }

        @Test
        fun `getValue delegate returns current config`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            val cfg: RefConfig by ref
            assertEquals("test-app", cfg.name)
            assertEquals(8080, cfg.port)
        }

        @Test
        fun `file is created with defaults when it does not exist`() {
            val file = tempDir.resolve("new-config.yml").toFile()
            assertTrue(!file.exists())

            val ref = YamlConfigManager.ref<RefConfig>(file)

            assertTrue(file.exists())
            assertEquals(RefConfig(), ref())
        }
    }

    @Nested
    inner class Reload {

        @Test
        fun `reload picks up changes from disk`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            assertEquals("test-app", ref().name)

            // Modify file on disk
            file.writeText("""
                name: updated-app
                port: 9090
                debug: true
                tags:
                  - x
                database:
                  host: db.prod
                  port: 5432
            """.trimIndent())

            val fresh = ref.reload()

            assertEquals("updated-app", fresh.name)
            assertEquals(9090, fresh.port)
            assertEquals(true, fresh.debug)
            assertEquals("updated-app", ref().name) // invoke also reflects new value
            assertEquals("updated-app", ref.current.name) // current also reflects
        }

        @Test
        fun `reload updates delegates`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            val cfg: RefConfig by ref
            assertEquals("test-app", cfg.name)

            file.writeText("""
                name: delegate-test
                port: 8080
                debug: false
                tags: []
                database:
                  host: localhost
                  port: 3306
            """.trimIndent())

            ref.reload()

            assertEquals("delegate-test", cfg.name) // delegate sees new value
        }
    }

    @Nested
    inner class Selecting {

        @Test
        fun `selecting delegate extracts sub-section`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            val db by ref.selecting { it.database }
            val name by ref.selecting { it.name }

            assertEquals("localhost", db.host)
            assertEquals(3306, db.port)
            assertEquals("test-app", name)
        }

        @Test
        fun `selecting delegate updates after reload`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            val db by ref.selecting { it.database }
            val port by ref.selecting { it.port }

            assertEquals("localhost", db.host)
            assertEquals(8080, port)

            file.writeText("""
                name: test
                port: 4000
                debug: false
                tags: []
                database:
                  host: db.new
                  port: 5432
            """.trimIndent())

            ref.reload()

            assertEquals("db.new", db.host)
            assertEquals(5432, db.port)
            assertEquals(4000, port)
        }

        @Test
        fun `selecting with computed value`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            val jdbcUrl by ref.selecting { "jdbc:mysql://${it.database.host}:${it.database.port}" }

            assertEquals("jdbc:mysql://localhost:3306", jdbcUrl)

            file.writeText("""
                name: test
                port: 8080
                debug: false
                tags: []
                database:
                  host: prod-db
                  port: 5432
            """.trimIndent())

            ref.reload()

            assertEquals("jdbc:mysql://prod-db:5432", jdbcUrl)
        }
    }

    @Nested
    inner class OnChange {

        @Test
        fun `onChange fires when config changes`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            var oldName = ""
            var newName = ""
            var callCount = 0

            ref.onChange { old, new ->
                oldName = old.name
                newName = new.name
                callCount++
            }

            file.writeText("""
                name: changed
                port: 8080
                debug: false
                tags: []
                database:
                  host: localhost
                  port: 3306
            """.trimIndent())

            ref.reload()

            assertEquals(1, callCount)
            assertEquals("test-app", oldName)
            assertEquals("changed", newName)
        }

        @Test
        fun `onChange does not fire when config is identical`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            var callCount = 0
            ref.onChange { _, _ -> callCount++ }

            // Reload without changing file content
            ref.reload()

            assertEquals(0, callCount)
        }

        @Test
        fun `multiple onChange listeners all fire`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            var count1 = 0
            var count2 = 0
            var count3 = 0

            ref.onChange { _, _ -> count1++ }
                .onChange { _, _ -> count2++ }
                .onChange { _, _ -> count3++ }

            file.writeText("""
                name: multi-listener
                port: 8080
                debug: false
                tags: []
                database:
                  host: localhost
                  port: 3306
            """.trimIndent())

            ref.reload()

            assertEquals(1, count1)
            assertEquals(1, count2)
            assertEquals(1, count3)
        }
    }

    @Nested
    inner class Chaining {

        @Test
        fun `fluent chaining API`() {
            val file = tempDir.resolve("config.yml").toFile()
            var changed = false

            val ref = YamlConfigManager.ref<RefConfig>(file)
                .onChange { _, _ -> changed = true }

            assertEquals("test-app", ref().name)

            file.writeText("""
                name: chained
                port: 8080
                debug: false
                tags: []
                database:
                  host: localhost
                  port: 3306
            """.trimIndent())

            ref.reload()

            assertTrue(changed)
            assertEquals("chained", ref().name)
        }
    }

    @Nested
    inner class Save {

        @Test
        fun `save writes current config back to file`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            ref.save()

            val content = file.readText()
            assertTrue(content.contains("test-app"))
            assertTrue(content.contains("8080"))
            assertTrue(content.contains("# App name"))
        }
    }

    @Nested
    inner class SharedRef {

        @Test
        fun `multiple consumers sharing same ref see reload simultaneously`() {
            val file = tempDir.resolve("config.yml").toFile()
            val ref = YamlConfigManager.ref<RefConfig>(file)

            // Simulate two managers sharing the same ref
            val name1 by ref.selecting { it.name }
            val port1 by ref.selecting { it.port }
            val name2 by ref.selecting { it.name }
            val db2 by ref.selecting { it.database }

            assertEquals("test-app", name1)
            assertEquals("test-app", name2)
            assertEquals(8080, port1)
            assertEquals("localhost", db2.host)

            file.writeText("""
                name: shared-update
                port: 3000
                debug: true
                tags: []
                database:
                  host: shared-db
                  port: 9999
            """.trimIndent())

            ref.reload()

            // All delegates see the new values
            assertEquals("shared-update", name1)
            assertEquals("shared-update", name2)
            assertEquals(3000, port1)
            assertEquals("shared-db", db2.host)
            assertEquals(9999, db2.port)
        }
    }
}
