package club.skidware.kconfig

import club.skidware.kconfig.migration.ConfigMigration
import club.skidware.kconfig.migration.MigrationRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationTest {

    private lateinit var runner: MigrationRunner

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        runner = MigrationRunner()
    }

    private fun migration(from: Int, to: Int, action: (MutableMap<String, Any?>) -> Unit): ConfigMigration {
        return object : ConfigMigration {
            override val fromVersion = from
            override val toVersion = to
            override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
                action(map)
                return map
            }
        }
    }

    @Nested
    inner class SingleMigration {

        @Test
        fun `single migration v1 to v2 renames field`() {
            runner.register(migration(1, 2) { map ->
                val old = map.remove("oldField")
                if (old != null) map["newField"] = old
            })

            val input = mutableMapOf<String, Any?>(
                "configVersion" to 1,
                "oldField" to "value123"
            )

            val result = runner.migrate(input, currentVersion = 2)

            assertEquals("value123", result["newField"])
            assertTrue(!result.containsKey("oldField"))
            assertEquals(2, result["configVersion"])
        }
    }

    @Nested
    inner class ChainMigration {

        @Test
        fun `chain migration v1 to v2 to v3`() {
            runner.register(migration(1, 2) { map ->
                // v1->v2: rename field
                val old = map.remove("legacy")
                if (old != null) map["updated"] = old
            })
            runner.register(migration(2, 3) { map ->
                // v2->v3: add a new computed field
                map["computed"] = "added-in-v3"
            })

            val input = mutableMapOf<String, Any?>(
                "configVersion" to 1,
                "legacy" to "original"
            )

            val result = runner.migrate(input, currentVersion = 3)

            assertEquals("original", result["updated"])
            assertEquals("added-in-v3", result["computed"])
            assertEquals(3, result["configVersion"])
            assertTrue(!result.containsKey("legacy"))
        }
    }

    @Nested
    inner class NoMigrationNeeded {

        @Test
        fun `config already at current version is a no-op`() {
            runner.register(migration(1, 2) { map ->
                map["shouldNotRun"] = true
            })

            val input = mutableMapOf<String, Any?>(
                "configVersion" to 2,
                "data" to "unchanged"
            )

            val result = runner.migrate(input, currentVersion = 2)

            assertEquals("unchanged", result["data"])
            assertTrue(!result.containsKey("shouldNotRun"))
        }
    }

    @Nested
    inner class MissingConfigVersion {

        @Test
        fun `missing configVersion field defaults to v1`() {
            runner.register(migration(1, 2) { map ->
                map["migrated"] = true
            })

            val input = mutableMapOf<String, Any?>(
                "data" to "old"
            )

            val result = runner.migrate(input, currentVersion = 2)

            assertEquals(true, result["migrated"])
            assertEquals(2, result["configVersion"])
        }
    }

    @Nested
    inner class BackupCreation {

        @Test
        fun `backup created before migration`() {
            runner.register(migration(1, 2) { map ->
                map["migrated"] = true
            })

            val sourceFile = tempDir.resolve("config.yml").toFile()
            sourceFile.writeText("configVersion: 1\ndata: original\n")

            val input = mutableMapOf<String, Any?>(
                "configVersion" to 1,
                "data" to "original"
            )

            runner.migrate(input, currentVersion = 2, sourceFile = sourceFile)

            val backupFile = File("${sourceFile.absolutePath}.v1.bak")
            assertTrue(backupFile.exists(), "Backup file should be created")
            assertEquals("configVersion: 1\ndata: original\n", backupFile.readText())

            // Cleanup
            backupFile.delete()
        }
    }

    @Nested
    inner class MissingMigrationStep {

        @Test
        fun `missing migration step throws with clear error message`() {
            // Only register v1->v2, but target is v3 (missing v2->v3)
            runner.register(migration(1, 2) { map ->
                map["step1"] = true
            })

            val input = mutableMapOf<String, Any?>(
                "configVersion" to 1,
                "data" to "test"
            )

            val exception = assertFailsWith<IllegalStateException> {
                runner.migrate(input, currentVersion = 3)
            }

            assertTrue(exception.message!!.contains("No migration found from version 2"))
        }
    }
}
