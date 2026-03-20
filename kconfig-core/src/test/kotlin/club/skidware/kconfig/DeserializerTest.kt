package club.skidware.kconfig

import club.skidware.kconfig.annotation.MigrateFrom
import club.skidware.kconfig.annotation.Pattern
import club.skidware.kconfig.annotation.Range
import club.skidware.kconfig.annotation.Transient
import club.skidware.kconfig.error.ConfigError
import club.skidware.kconfig.error.ConfigErrorCollector
import club.skidware.kconfig.reader.Deserializer
import club.skidware.kconfig.serializer.BuiltinSerializers
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SecretStringSerializer
import club.skidware.kconfig.serializer.SerializerRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

data class Primitives(
    val name: String = "default",
    val count: Int = 0,
    val bigNum: Long = 0L,
    val ratio: Double = 0.0,
    val weight: Float = 0f,
    val enabled: Boolean = false
)

data class Inner(val value: String = "inner")
data class Middle(val inner: Inner = Inner())
data class Outer(val middle: Middle = Middle(), val label: String = "top")

data class WithLists(
    val tags: List<String> = emptyList(),
    val nested: List<Inner> = emptyList()
)

enum class Color { RED, GREEN, BLUE }

data class WithMaps(
    val counts: Map<String, Int> = emptyMap(),
    val labels: Map<Color, String> = emptyMap()
)

data class WithDefaults(
    val required: String,
    val optional: String = "fallback",
    val count: Int = 42
)

data class NullableFields(
    val x: String? = null,
    val y: Int? = null
)

data class RequiredOnly(val name: String, val age: Int)

data class WithEnum(val color: Color = Color.RED)

data class TransientField(
    val name: String = "visible",
    @Transient val secret: String = "hidden-default"
)

data class Migrated(
    @MigrateFrom("old_name", "legacy_name") val name: String = "migrated"
)

data class WithRange(
    @Range(min = 0.0, max = 100.0) val score: Int = 50
)

data class WithRangeOptional(
    @Range(min = 0.0, max = 100.0) val score: Int = 50
)

data class WithPattern(
    @Pattern(regex = "^[a-z]+$", description = "lowercase only") val code: String = "abc"
)

data class WithPatternOptional(
    @Pattern(regex = "^[a-z]+$", description = "lowercase only") val code: String = "abc"
)

data class WithSecretString(
    val apiKey: SecretString = SecretString("default-key")
)

class DeserializerTest {

    private lateinit var registry: SerializerRegistry
    private lateinit var deserializer: Deserializer
    private lateinit var errors: ConfigErrorCollector

    @BeforeEach
    fun setup() {
        registry = SerializerRegistry()
        BuiltinSerializers.registerAll(registry)
        registry.register(SecretString::class, SecretStringSerializer)
        deserializer = Deserializer(registry)
        errors = ConfigErrorCollector()
    }

    @Nested
    inner class PrimitiveDeserialization {

        @Test
        fun `all primitive types deserialize correctly from map`() {
            val map = mapOf(
                "name" to "hello",
                "count" to 10,
                "bigNum" to 9999999999L,
                "ratio" to 3.14,
                "weight" to 1.5,
                "enabled" to true
            )

            val result = deserializer.deserialize(Primitives::class, map, errors)

            assertNotNull(result)
            assertEquals("hello", result.name)
            assertEquals(10, result.count)
            assertEquals(9999999999L, result.bigNum)
            assertEquals(3.14, result.ratio)
            assertEquals(1.5f, result.weight)
            assertEquals(true, result.enabled)
            assertTrue(!errors.hasErrors())
        }

        @Test
        fun `number type coercion works`() {
            val map = mapOf(
                "name" to "test",
                "count" to 5L,       // Long -> Int
                "bigNum" to 42,      // Int -> Long
                "ratio" to 2,
                "weight" to 3.0,
                "enabled" to false
            )

            val result = deserializer.deserialize(Primitives::class, map, errors)

            assertNotNull(result)
            assertEquals(5, result.count)
            assertEquals(42L, result.bigNum)
            assertEquals(2.0, result.ratio)
            assertEquals(3.0f, result.weight)
        }
    }

    @Nested
    inner class NestedDeserialization {

        @Test
        fun `nested data class 3 levels deep`() {
            val map = mapOf(
                "middle" to mapOf(
                    "inner" to mapOf(
                        "value" to "deep"
                    )
                ),
                "label" to "root"
            )

            val result = deserializer.deserialize(Outer::class, map, errors)

            assertNotNull(result)
            assertEquals("root", result.label)
            assertEquals("deep", result.middle.inner.value)
            assertTrue(!errors.hasErrors())
        }
    }

    @Nested
    inner class CollectionDeserialization {

        @Test
        fun `list of strings`() {
            val map = mapOf(
                "tags" to listOf("a", "b", "c"),
                "nested" to emptyList<Any>()
            )

            val result = deserializer.deserialize(WithLists::class, map, errors)

            assertNotNull(result)
            assertEquals(listOf("a", "b", "c"), result.tags)
            assertTrue(!errors.hasErrors())
        }

        @Test
        fun `list of nested data classes`() {
            val map = mapOf(
                "tags" to emptyList<Any>(),
                "nested" to listOf(
                    mapOf("value" to "first"),
                    mapOf("value" to "second")
                )
            )

            val result = deserializer.deserialize(WithLists::class, map, errors)

            assertNotNull(result)
            assertEquals(2, result.nested.size)
            assertEquals("first", result.nested[0].value)
            assertEquals("second", result.nested[1].value)
        }

        @Test
        fun `map of string to int`() {
            val map = mapOf(
                "counts" to mapOf("apples" to 3, "oranges" to 5),
                "labels" to emptyMap<String, String>()
            )

            val result = deserializer.deserialize(WithMaps::class, map, errors)

            assertNotNull(result)
            assertEquals(3, result.counts["apples"])
            assertEquals(5, result.counts["oranges"])
        }

        @Test
        fun `map with enum keys`() {
            val map = mapOf(
                "counts" to emptyMap<String, Int>(),
                "labels" to mapOf("RED" to "hot", "BLUE" to "cold")
            )

            val result = deserializer.deserialize(WithMaps::class, map, errors)

            assertNotNull(result)
            assertEquals("hot", result.labels[Color.RED])
            assertEquals("cold", result.labels[Color.BLUE])
        }
    }

    @Nested
    inner class DefaultsAndMissingKeys {

        @Test
        fun `missing keys use Kotlin defaults when param is optional`() {
            val map = mapOf("required" to "provided")

            val result = deserializer.deserialize(WithDefaults::class, map, errors)

            assertNotNull(result)
            assertEquals("provided", result.required)
            assertEquals("fallback", result.optional)
            assertEquals(42, result.count)
        }

        @Test
        fun `empty map uses all defaults`() {
            val map = emptyMap<String, Any?>()

            val result = deserializer.deserialize(Primitives::class, map, errors)

            assertNotNull(result)
            assertEquals("default", result.name)
            assertEquals(0, result.count)
            assertEquals(0L, result.bigNum)
            assertEquals(0.0, result.ratio)
            assertEquals(0f, result.weight)
            assertEquals(false, result.enabled)
        }
    }

    @Nested
    inner class UnknownKeys {

        @Test
        fun `extra unknown keys collected as UnknownKey errors with suggestions`() {
            val map = mapOf(
                "name" to "test",
                "naem" to "typo",       // typo of "name"
                "count" to 1,
                "bigNum" to 1L,
                "ratio" to 1.0,
                "weight" to 1f,
                "enabled" to true
            )

            deserializer.deserialize(Primitives::class, map, errors)

            assertTrue(errors.hasErrors())
            val unknownErrors = errors.all().filterIsInstance<ConfigError.UnknownKey>()
            assertEquals(1, unknownErrors.size)
            assertEquals("naem", unknownErrors[0].path)
            assertEquals("name", unknownErrors[0].suggestion)
        }
    }

    @Nested
    inner class NullableHandling {

        @Test
        fun `nullable field with null value in map`() {
            val map = mapOf("x" to null, "y" to null)

            val result = deserializer.deserialize(NullableFields::class, map, errors)

            assertNotNull(result)
            assertNull(result.x)
            assertNull(result.y)
            assertTrue(!errors.hasErrors())
        }

        @Test
        fun `nullable field missing from map uses default null`() {
            val map = emptyMap<String, Any?>()

            val result = deserializer.deserialize(NullableFields::class, map, errors)

            assertNotNull(result)
            assertNull(result.x)
            assertNull(result.y)
        }
    }

    @Nested
    inner class ErrorCases {

        @Test
        fun `missing required non-nullable field produces MissingRequired error`() {
            val map = emptyMap<String, Any?>()

            val result = deserializer.deserialize(RequiredOnly::class, map, errors)

            assertTrue(errors.hasErrors())
            val missing = errors.all().filterIsInstance<ConfigError.MissingRequired>()
            assertTrue(missing.isNotEmpty())
        }

        @Test
        fun `wrong type value produces InvalidValue error`() {
            val map = mapOf(
                "name" to "ok",
                "count" to "not-a-number",
                "bigNum" to 1L,
                "ratio" to 1.0,
                "weight" to 1.0,
                "enabled" to true
            )

            deserializer.deserialize(Primitives::class, map, errors)

            assertTrue(errors.hasErrors())
            val invalid = errors.all().filterIsInstance<ConfigError.InvalidValue>()
            assertTrue(invalid.isNotEmpty())
            assertTrue(invalid.any { it.path == "count" })
        }
    }

    @Nested
    inner class EnumDeserialization {

        @Test
        fun `enum deserialization is case-insensitive`() {
            val map = mapOf("color" to "green")

            val result = deserializer.deserialize(WithEnum::class, map, errors)

            assertNotNull(result)
            assertEquals(Color.GREEN, result.color)
            assertTrue(!errors.hasErrors())
        }

        @Test
        fun `invalid enum value produces error with suggestion`() {
            val map = mapOf("color" to "GREE")

            deserializer.deserialize(WithEnum::class, map, errors)

            assertTrue(errors.hasErrors())
            val invalid = errors.all().filterIsInstance<ConfigError.InvalidValue>()
            assertTrue(invalid.isNotEmpty())
        }
    }

    @Nested
    inner class TransientFields {

        @Test
        fun `transient field always uses default and ignores YAML value`() {
            val map = mapOf(
                "name" to "custom",
                "secret" to "should-be-ignored"
            )

            val result = deserializer.deserialize(TransientField::class, map, errors)

            assertNotNull(result)
            assertEquals("custom", result.name)
            assertEquals("hidden-default", result.secret)
        }
    }

    @Nested
    inner class MigrateFromAnnotation {

        @Test
        fun `old key name in map resolves via MigrateFrom`() {
            val map = mapOf("old_name" to "legacy-value")

            val result = deserializer.deserialize(Migrated::class, map, errors)

            assertNotNull(result)
            assertEquals("legacy-value", result.name)
        }

        @Test
        fun `second old key name resolves when first is missing`() {
            val map = mapOf("legacy_name" to "very-old-value")

            val result = deserializer.deserialize(Migrated::class, map, errors)

            assertNotNull(result)
            assertEquals("very-old-value", result.name)
        }
    }

    @Nested
    inner class RangeValidation {

        @Test
        fun `out of range value produces OutOfRange error and falls back to default`() {
            val map = mapOf("score" to 200)

            val result = deserializer.deserialize(WithRangeOptional::class, map, errors)

            assertNotNull(result)
            assertTrue(errors.hasErrors())
            val rangeErrors = errors.all().filterIsInstance<ConfigError.OutOfRange>()
            assertTrue(rangeErrors.isNotEmpty())
            // Falls back to default because param is optional
            assertEquals(50, result.score)
        }

        @Test
        fun `in-range value passes validation`() {
            val map = mapOf("score" to 75)

            val result = deserializer.deserialize(WithRange::class, map, errors)

            assertNotNull(result)
            assertEquals(75, result.score)
            assertTrue(!errors.hasErrors())
        }
    }

    @Nested
    inner class PatternValidation {

        @Test
        fun `pattern mismatch produces PatternMismatch error and falls back to default`() {
            val map = mapOf("code" to "ABC123")

            val result = deserializer.deserialize(WithPatternOptional::class, map, errors)

            assertNotNull(result)
            assertTrue(errors.hasErrors())
            val patternErrors = errors.all().filterIsInstance<ConfigError.PatternMismatch>()
            assertTrue(patternErrors.isNotEmpty())
            // Falls back to default because param is optional
            assertEquals("abc", result.code)
        }

        @Test
        fun `matching pattern passes validation`() {
            val map = mapOf("code" to "valid")

            val result = deserializer.deserialize(WithPattern::class, map, errors)

            assertNotNull(result)
            assertEquals("valid", result.code)
            assertTrue(!errors.hasErrors())
        }
    }

    @Nested
    inner class SecretStringDeserialization {

        @Test
        fun `SecretString field deserializes from string value`() {
            val map = mapOf("apiKey" to "my-secret-api-key")

            val result = deserializer.deserialize(WithSecretString::class, map, errors)

            assertNotNull(result)
            assertEquals("my-secret-api-key", result.apiKey.expose())
            assertEquals("********", result.apiKey.toString())
            assertTrue(!errors.hasErrors())
        }
    }
}
