package club.skidware.kconfig.reader

import club.skidware.kconfig.annotation.Env
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Post-deserialization resolver that overrides data class field values with environment variables
 * when the field is annotated with [@Env][Env].
 *
 * **Priority order** (highest wins):
 * 1. Environment variable (via [System.getenv]) -- if the variable is set and non-null.
 * 2. YAML value -- the value already deserialized into the instance.
 * 3. Kotlin default -- the compile-time default declared in the data class constructor.
 *
 * This design allows operators to override configuration at deploy time without modifying the
 * YAML file, which is essential for secrets (database passwords, API tokens) that should never
 * be committed to version control.
 *
 * **Security note:** Environment variable values are read via [System.getenv] and converted to
 * the target type without sanitisation. Sensitive values should use
 * [SecretString][club.skidware.kconfig.serializer.SecretString] so that they are masked in
 * logs and `toString()` output.
 *
 * The resolver operates recursively: nested data classes are walked so that `@Env` annotations
 * at any depth are honoured.
 *
 * Example:
 * ```kotlin
 * data class Database(
 *     val host: String = "localhost",
 *     @Env("DB_PASSWORD") val password: String = ""
 * )
 * data class AppConfig(val database: Database = Database())
 *
 * // After deserialization from YAML:
 * val config = EnvOverrideResolver.resolve(deserialized, AppConfig::class)
 * // If DB_PASSWORD=hunter2 is set in the environment, config.database.password == "hunter2"
 * ```
 *
 * @see Env
 * @see Deserializer
 * @since 1.0
 */
object EnvOverrideResolver {

    /**
     * Creates a new instance of [T] where any field annotated with [@Env][Env] is replaced
     * by the corresponding environment variable value, if set.
     *
     * The method walks the primary constructor parameters of [klass], reads the current
     * property value from [instance], checks for an `@Env` annotation, and queries
     * [System.getenv]. For nested data class parameters the method recurses.
     *
     * If no environment variable overrides any field, the original [instance] is returned
     * (identity equality, no copy).
     *
     * @param T The data class type.
     * @param instance The already-deserialized data class instance.
     * @param klass The [KClass] token for [T], used for reflection.
     * @return A new instance with overridden fields, or the same [instance] if nothing changed.
     * @throws NumberFormatException If an environment variable value cannot be converted to the
     *         target numeric type (e.g. `"abc"` for an `Int` field).
     */
    fun <T : Any> resolve(instance: T, klass: KClass<T>): T {
        val constructor = klass.primaryConstructor ?: return instance
        val properties = klass.memberProperties.associateBy { it.name }
        val args = mutableMapOf<KParameter, Any?>()
        var changed = false

        for (param in constructor.parameters) {
            val name = param.name ?: continue
            val prop = properties[name] ?: continue
            val currentValue = prop.getter.call(instance)

            val result = this.resolveParameter(param, currentValue)
            if (result !== currentValue) {
                changed = true
            }
            args[param] = result
        }

        if (!changed) return instance

        @Suppress("UNCHECKED_CAST")
        return constructor.callBy(args) as T
    }

    /**
     * Resolves a single constructor parameter by checking for an `@Env` override
     * or recursing into nested data classes.
     *
     * @param param The constructor parameter to resolve.
     * @param currentValue The current value of the parameter on the instance.
     * @return The resolved value -- either an env override, a recursively resolved
     *         nested data class, or the original [currentValue].
     */
    private fun resolveParameter(param: KParameter, currentValue: Any?): Any? {
        val envOverride = this.resolveEnvValue(param, currentValue)
        if (envOverride != null) return envOverride

        // Recurse for nested data classes
        val paramKlass = param.type.classifier as? KClass<*>
        if (paramKlass != null && paramKlass.isData && currentValue != null) {
            @Suppress("UNCHECKED_CAST")
            return this.resolve(currentValue as Any, paramKlass as KClass<Any>)
        }

        return currentValue
    }

    /**
     * Checks whether the given [param] has an `@Env` annotation and, if the
     * corresponding environment variable is set, converts and returns the value.
     *
     * @param param The constructor parameter to check.
     * @param currentValue The current value, used to avoid unnecessary replacement.
     * @return The converted environment variable value if it differs from [currentValue],
     *         or `null` if no override applies.
     */
    private fun resolveEnvValue(param: KParameter, currentValue: Any?): Any? {
        val envAnn = param.findAnnotation<Env>() ?: return null
        val envValue = System.getenv(envAnn.variable) ?: return null
        val converted = this.convertEnvValue(envValue, param.type.classifier as? KClass<*>)
        if (converted == currentValue) return null
        return converted
    }

    /**
     * Converts a raw environment variable string to the appropriate JVM type.
     *
     * Supports [Int], [Long], [Double], [Float], and [Boolean]. All other types
     * (including [String]) are returned as-is.
     *
     * @param value The raw environment variable value.
     * @param targetClass The expected Kotlin type, or `null` if unknown.
     * @return The converted value.
     * @throws NumberFormatException If a numeric conversion fails.
     */
    private fun convertEnvValue(value: String, targetClass: KClass<*>?): Any {
        return when (targetClass) {
            Int::class -> value.toInt()
            Long::class -> value.toLong()
            Double::class -> value.toDouble()
            Float::class -> value.toFloat()
            Boolean::class -> value.toBoolean()
            else -> value
        }
    }
}
