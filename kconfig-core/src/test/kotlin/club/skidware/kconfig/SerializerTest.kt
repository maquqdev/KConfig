package club.skidware.kconfig

import club.skidware.kconfig.annotation.Transient
import club.skidware.kconfig.serializer.BuiltinSerializers
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SecretStringSerializer
import club.skidware.kconfig.serializer.SerializerRegistry
import club.skidware.kconfig.writer.Serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ---- Test data classes for serialization ----

data class SimpleConfig(
    val name: String = "test",
    val count: Int = 5,
    val enabled: Boolean = true
)

enum class Priority { LOW, MEDIUM, HIGH }

data class EnumConfig(val priority: Priority = Priority.MEDIUM)

data class InnerConfig(val host: String = "localhost", val port: Int = 8080)
data class NestedConfig(val server: InnerConfig = InnerConfig(), val debug: Boolean = false)

data class ListMapConfig(
    val tags: List<String> = listOf("a", "b"),
    val scores: Map<String, Int> = mapOf("x" to 1, "y" to 2)
)

data class TransientConfig(
    val visible: String = "shown",
    @Transient val hidden: String = "secret"
)

data class SecretConfig(
    val apiKey: SecretString = SecretString("super-secret")
)

data class OrderedConfig(
    val alpha: String = "a",
    val beta: String = "b",
    val gamma: String = "c",
    val delta: String = "d"
)

class SerializerTest {

    private lateinit var registry: SerializerRegistry

    @BeforeEach
    fun setup() {
        registry = SerializerRegistry()
        BuiltinSerializers.registerAll(registry)
        registry.register(SecretString::class, SecretStringSerializer)
    }

    @Nested
    inner class SimpleDataClass {

        @Test
        fun `simple data class serializes to LinkedHashMap with correct keys and values`() {
            val config = SimpleConfig(name = "hello", count = 10, enabled = false)

            val result = Serializer.serialize(config, registry)

            assertIs<LinkedHashMap<*, *>>(result)
            assertEquals("hello", result["name"])
            assertEquals(10, result["count"])
            assertEquals(false, result["enabled"])
            assertEquals(3, result.size)
        }
    }

    @Nested
    inner class NestedSerialization {

        @Test
        fun `nested data class serializes to nested maps`() {
            val config = NestedConfig(
                server = InnerConfig(host = "example.com", port = 9090),
                debug = true
            )

            val result = Serializer.serialize(config, registry)

            val serverMap = result["server"]
            assertIs<Map<*, *>>(serverMap)
            assertEquals("example.com", serverMap["host"])
            assertEquals(9090, serverMap["port"])
            assertEquals(true, result["debug"])
        }
    }

    @Nested
    inner class EnumSerialization {

        @Test
        fun `enum serialized as name string`() {
            val config = EnumConfig(Priority.HIGH)

            val result = Serializer.serialize(config, registry)

            assertEquals("HIGH", result["priority"])
        }
    }

    @Nested
    inner class CollectionSerialization {

        @Test
        fun `list serialization`() {
            val config = ListMapConfig(tags = listOf("x", "y", "z"), scores = emptyMap())

            val result = Serializer.serialize(config, registry)

            val tags = result["tags"]
            assertIs<List<*>>(tags)
            assertEquals(listOf("x", "y", "z"), tags)
        }

        @Test
        fun `map serialization`() {
            val config = ListMapConfig(tags = emptyList(), scores = mapOf("a" to 10, "b" to 20))

            val result = Serializer.serialize(config, registry)

            val scores = result["scores"]
            assertIs<Map<*, *>>(scores)
            assertEquals(10, scores["a"])
            assertEquals(20, scores["b"])
        }
    }

    @Nested
    inner class TransientSerialization {

        @Test
        fun `transient fields are omitted from output`() {
            val config = TransientConfig(visible = "yes", hidden = "no")

            val result = Serializer.serialize(config, registry)

            assertTrue(result.containsKey("visible"))
            assertFalse(result.containsKey("hidden"))
            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class SecretStringSerialization {

        @Test
        fun `SecretString serialized as plaintext for file writing`() {
            val config = SecretConfig(apiKey = SecretString("my-api-key-123"))

            val result = Serializer.serialize(config, registry)

            assertEquals("my-api-key-123", result["apiKey"])
        }
    }

    @Nested
    inner class FieldOrdering {

        @Test
        fun `field ordering matches constructor parameter order`() {
            val config = OrderedConfig()

            val result = Serializer.serialize(config, registry)

            val keys = result.keys.toList()
            assertEquals(listOf("alpha", "beta", "gamma", "delta"), keys)
        }
    }
}
