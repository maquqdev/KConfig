package club.skidware.kconfig.serializer

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Thread-safe registry that maps Kotlin types to their [TypeSerializer] instances.
 *
 * The registry uses a [ConcurrentHashMap] internally, so it is safe to read
 * and write from multiple threads without external synchronization.
 *
 * Example -- registering and retrieving serializers:
 * ```kotlin
 * val registry = SerializerRegistry()
 *
 * // Register built-in serializers
 * BuiltinSerializers.registerAll(registry)
 *
 * // Register a custom serializer
 * registry.register(Instant::class, InstantSerializer)
 *
 * // Retrieve a serializer
 * val intSerializer = registry.get(Int::class)     // TypeSerializer<Int>?
 * val exists        = registry.has(String::class)   // true
 * ```
 *
 * @see TypeSerializer
 * @see BuiltinSerializers
 * @since 1.0
 */
class SerializerRegistry {

    /** Concurrent backing store keyed by [KClass]. */
    private val map = ConcurrentHashMap<KClass<*>, TypeSerializer<*>>()

    /**
     * Registers a [serializer] for the given Kotlin [klass].
     *
     * If a serializer is already registered for [klass], it is silently
     * replaced.
     *
     * @param T The type handled by the serializer.
     * @param klass The [KClass] token for [T].
     * @param serializer The [TypeSerializer] instance to register.
     */
    fun <T : Any> register(klass: KClass<T>, serializer: TypeSerializer<T>) {
        this.map[klass] = serializer
    }

    /**
     * Returns the [TypeSerializer] registered for the given [klass], or
     * `null` if none has been registered.
     *
     * @param T The expected type.
     * @param klass The [KClass] token to look up.
     * @return The registered serializer cast to [TypeSerializer]<[T]>, or `null`.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(klass: KClass<T>): TypeSerializer<T>? {
        return this.map[klass] as? TypeSerializer<T>
    }

    /**
     * Checks whether a serializer is registered for the given [klass].
     *
     * @param klass The [KClass] token to check.
     * @return `true` if a serializer exists for [klass], `false` otherwise.
     */
    fun has(klass: KClass<*>): Boolean = this.map.containsKey(klass)
}
