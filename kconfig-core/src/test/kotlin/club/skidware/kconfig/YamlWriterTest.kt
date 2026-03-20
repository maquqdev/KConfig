package club.skidware.kconfig

import club.skidware.kconfig.annotation.CommentPlacement
import club.skidware.kconfig.writer.CommentData
import club.skidware.kconfig.writer.YamlWriter
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlWriterTest {

    @Nested
    inner class FlatMaps {

        @Test
        fun `simple flat map produces proper YAML`() {
            val data = linkedMapOf(
                "name" to "hello",
                "count" to 42,
                "enabled" to true
            )

            val yaml = YamlWriter.writeToString(data)

            val lines = yaml.lines().filter { it.isNotBlank() }
            assertEquals("name: hello", lines[0])
            assertEquals("count: 42", lines[1])
            assertEquals("enabled: true", lines[2])
        }
    }

    @Nested
    inner class NestedMaps {

        @Test
        fun `nested map is indented correctly`() {
            val data = linkedMapOf<String, Any?>(
                "server" to linkedMapOf(
                    "host" to "localhost",
                    "port" to 8080
                )
            )

            val yaml = YamlWriter.writeToString(data)

            assertTrue(yaml.contains("server:"))
            assertTrue(yaml.contains("  host: localhost"))
            assertTrue(yaml.contains("  port: 8080"))
        }

        @Test
        fun `deeply nested map indents correctly at each level`() {
            val data = linkedMapOf<String, Any?>(
                "level1" to linkedMapOf<String, Any?>(
                    "level2" to linkedMapOf(
                        "value" to "deep"
                    )
                )
            )

            val yaml = YamlWriter.writeToString(data)

            assertTrue(yaml.contains("level1:"))
            assertTrue(yaml.contains("  level2:"))
            assertTrue(yaml.contains("    value: deep"))
        }
    }

    @Nested
    inner class ListFormatting {

        @Test
        fun `list of primitives uses dash format`() {
            val data = linkedMapOf<String, Any?>(
                "tags" to listOf("alpha", "beta", "gamma")
            )

            val yaml = YamlWriter.writeToString(data)

            assertTrue(yaml.contains("tags:"))
            assertTrue(yaml.contains("  - alpha"))
            assertTrue(yaml.contains("  - beta"))
            assertTrue(yaml.contains("  - gamma"))
        }

        @Test
        fun `list of maps uses complex list format`() {
            val data = linkedMapOf<String, Any?>(
                "servers" to listOf(
                    linkedMapOf("host" to "a.com", "port" to 80),
                    linkedMapOf("host" to "b.com", "port" to 443)
                )
            )

            val yaml = YamlWriter.writeToString(data)

            assertTrue(yaml.contains("servers:"))
            assertTrue(yaml.contains("  - host: a.com"))
            assertTrue(yaml.contains("    port: 80"))
            assertTrue(yaml.contains("  - host: b.com"))
            assertTrue(yaml.contains("    port: 443"))
        }

        @Test
        fun `empty list produces bracket notation`() {
            val data = linkedMapOf<String, Any?>(
                "items" to emptyList<Any>()
            )

            val yaml = YamlWriter.writeToString(data)

            assertTrue(yaml.contains("items: []"))
        }
    }

    @Nested
    inner class StringQuoting {

        @Test
        fun `string with colon is quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "value: with colon")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("\"value: with colon\""))
        }

        @Test
        fun `string with hash is quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "value # comment")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("\"value # comment\""))
        }

        @Test
        fun `boolean-like string is quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "true")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("\"true\""))
        }

        @Test
        fun `numeric string is quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "123")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("\"123\""))
        }

        @Test
        fun `empty string is quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("key: \"\""))
        }

        @Test
        fun `plain string is not quoted`() {
            val data = linkedMapOf<String, Any?>("key" to "hello")
            val yaml = YamlWriter.writeToString(data)
            assertTrue(yaml.contains("key: hello"))
        }
    }

    @Nested
    inner class Comments {

        @Test
        fun `ABOVE comment is placed before the key`() {
            val data = linkedMapOf<String, Any?>("name" to "test")
            val comments = mapOf(
                "name" to CommentData(listOf("This is the name"), CommentPlacement.ABOVE)
            )

            val yaml = YamlWriter.writeToString(data, comments)

            val lines = yaml.lines()
            val commentIdx = lines.indexOfFirst { it.contains("# This is the name") }
            val valueIdx = lines.indexOfFirst { it.contains("name: test") }
            assertTrue(commentIdx >= 0, "Comment should be present")
            assertTrue(valueIdx >= 0, "Value should be present")
            assertTrue(commentIdx < valueIdx, "Comment should be before value")
        }

        @Test
        fun `INLINE comment appears on same line as flat value`() {
            val data = linkedMapOf<String, Any?>("port" to 8080)
            val comments = mapOf(
                "port" to CommentData(listOf("server port"), CommentPlacement.INLINE)
            )

            val yaml = YamlWriter.writeToString(data, comments)

            assertTrue(yaml.contains("port: 8080  # server port"))
        }

        @Test
        fun `INLINE on section falls back to ABOVE`() {
            val data = linkedMapOf<String, Any?>(
                "server" to linkedMapOf(
                    "host" to "localhost"
                )
            )
            val comments = mapOf(
                "server" to CommentData(listOf("server config"), CommentPlacement.INLINE)
            )

            val yaml = YamlWriter.writeToString(data, comments)

            // For a section, inline should fall back to above
            val lines = yaml.lines()
            val commentIdx = lines.indexOfFirst { it.contains("# server config") }
            val sectionIdx = lines.indexOfFirst { it.trimEnd() == "server:" }
            assertTrue(commentIdx >= 0, "Comment should be present")
            assertTrue(sectionIdx >= 0, "Section should be present")
            assertTrue(commentIdx < sectionIdx, "Comment should appear before section key")
        }

        @Test
        fun `multi-line comment always placed ABOVE`() {
            val data = linkedMapOf<String, Any?>("timeout" to 30)
            val comments = mapOf(
                "timeout" to CommentData(
                    listOf("Connection timeout", "in seconds"),
                    CommentPlacement.INLINE  // even if INLINE, multi-line goes ABOVE
                )
            )

            val yaml = YamlWriter.writeToString(data, comments)

            val lines = yaml.lines()
            assertTrue(lines.any { it.contains("# Connection timeout") })
            assertTrue(lines.any { it.contains("# in seconds") })
            // Both comments should be before the value
            val firstCommentIdx = lines.indexOfFirst { it.contains("# Connection timeout") }
            val valueIdx = lines.indexOfFirst { it.contains("timeout: 30") }
            assertTrue(firstCommentIdx < valueIdx)
        }
    }
}
