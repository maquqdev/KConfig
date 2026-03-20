package club.skidware.kconfig.serializer

/**
 * Provides built-in [TypeSerializer] implementations for Kotlin's standard
 * primitive and string types.
 *
 * Supported types and their coercion rules:
 *
 * | Target type | Accepted raw types | Coercion notes |
 * |-------------|-------------------|----------------|
 * | [String]    | Any               | Calls `toString()` |
 * | [Int]       | [Number], [String] | Numeric narrowing via `toInt()`; string parsed with `toIntOrNull()` |
 * | [Long]      | [Number], [String] | Numeric widening via `toLong()`; string parsed with `toLongOrNull()` |
 * | [Double]    | [Number], [String] | Numeric widening via `toDouble()`; string parsed with `toDoubleOrNull()` |
 * | [Float]     | [Number], [String] | Numeric narrowing via `toFloat()`; string parsed with `toFloatOrNull()` |
 * | [Boolean]   | [Boolean], [String], [Number] | Strings must be `"true"` / `"false"` (case-insensitive); numbers use `!= 0` |
 *
 * Example:
 * ```kotlin
 * val registry = SerializerRegistry()
 * BuiltinSerializers.registerAll(registry)
 *
 * val intSer = registry.get(Int::class)!!
 * intSer.deserialize("42")   // 42
 * intSer.deserialize(42L)    // 42  (Long → Int coercion)
 *
 * val boolSer = registry.get(Boolean::class)!!
 * boolSer.deserialize("true") // true
 * boolSer.deserialize(1)      // true  (non-zero → true)
 * ```
 *
 * @see TypeSerializer
 * @see SerializerRegistry
 * @since 1.0
 */
object BuiltinSerializers {

    /**
     * Registers all built-in serializers ([StringSerializer], [IntSerializer],
     * [LongSerializer], [DoubleSerializer], [FloatSerializer], and
     * [BooleanSerializer]) into the given [registry].
     *
     * This is typically called once during application initialization.
     *
     * @param registry The [SerializerRegistry] to populate.
     */
    fun registerAll(registry: SerializerRegistry) {
        registry.register(String::class, StringSerializer)
        registry.register(Int::class, IntSerializer)
        registry.register(Long::class, LongSerializer)
        registry.register(Double::class, DoubleSerializer)
        registry.register(Float::class, FloatSerializer)
        registry.register(Boolean::class, BooleanSerializer)
    }

    /**
     * Serializer for [String] values.
     *
     * Serialization is identity; deserialization calls `toString()` on any
     * raw value.
     *
     * @since 1.0
     */
    object StringSerializer : TypeSerializer<String> {
        override fun serialize(value: String): Any = value
        override fun deserialize(raw: Any): String = raw.toString()
    }

    /**
     * Serializer for [Int] values.
     *
     * Deserialization accepts [Number] (via `toInt()`) and [String] (via
     * `toIntOrNull()`). All other types cause an [IllegalArgumentException].
     *
     * @since 1.0
     */
    object IntSerializer : TypeSerializer<Int> {
        override fun serialize(value: Int): Any = value
        override fun deserialize(raw: Any): Int = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull() ?: throw IllegalArgumentException("Cannot convert '$raw' to Int")
            else -> throw IllegalArgumentException("Cannot convert ${raw::class.simpleName} to Int")
        }
    }

    /**
     * Serializer for [Long] values.
     *
     * Deserialization accepts [Number] (via `toLong()`) and [String] (via
     * `toLongOrNull()`). All other types cause an [IllegalArgumentException].
     *
     * @since 1.0
     */
    object LongSerializer : TypeSerializer<Long> {
        override fun serialize(value: Long): Any = value
        override fun deserialize(raw: Any): Long = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: throw IllegalArgumentException("Cannot convert '$raw' to Long")
            else -> throw IllegalArgumentException("Cannot convert ${raw::class.simpleName} to Long")
        }
    }

    /**
     * Serializer for [Double] values.
     *
     * Deserialization accepts [Number] (via `toDouble()`) and [String] (via
     * `toDoubleOrNull()`). All other types cause an [IllegalArgumentException].
     *
     * @since 1.0
     */
    object DoubleSerializer : TypeSerializer<Double> {
        override fun serialize(value: Double): Any = value
        override fun deserialize(raw: Any): Double = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: throw IllegalArgumentException("Cannot convert '$raw' to Double")
            else -> throw IllegalArgumentException("Cannot convert ${raw::class.simpleName} to Double")
        }
    }

    /**
     * Serializer for [Float] values.
     *
     * Deserialization accepts [Number] (via `toFloat()`) and [String] (via
     * `toFloatOrNull()`). All other types cause an [IllegalArgumentException].
     *
     * @since 1.0
     */
    object FloatSerializer : TypeSerializer<Float> {
        override fun serialize(value: Float): Any = value
        override fun deserialize(raw: Any): Float = when (raw) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull() ?: throw IllegalArgumentException("Cannot convert '$raw' to Float")
            else -> throw IllegalArgumentException("Cannot convert ${raw::class.simpleName} to Float")
        }
    }

    /**
     * Serializer for [Boolean] values.
     *
     * Deserialization rules:
     * - [Boolean] -- returned as-is.
     * - [String] -- parsed case-insensitively; only `"true"` and `"false"` are
     *   accepted. Any other string throws [IllegalArgumentException].
     * - [Number] -- `0` maps to `false`, any non-zero value maps to `true`.
     *
     * @since 1.0
     */
    object BooleanSerializer : TypeSerializer<Boolean> {
        override fun serialize(value: Boolean): Any = value
        override fun deserialize(raw: Any): Boolean = when (raw) {
            is Boolean -> raw
            is String -> raw.lowercase().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("Cannot convert '$raw' to Boolean")
            is Number -> raw.toInt() != 0
            else -> throw IllegalArgumentException("Cannot convert ${raw::class.simpleName} to Boolean")
        }
    }
}
