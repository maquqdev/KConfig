package club.skidware.kconfig.serializer

import club.skidware.kconfig.annotation.MaskStrategy
import club.skidware.kconfig.annotation.Secret
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Reflective utility that inspects a data class hierarchy and extracts
 * metadata for every field that should be treated as a secret.
 *
 * A field is considered secret if **either** of the following is true:
 * 1. It is annotated with [@Secret][Secret].
 * 2. Its type is [SecretString] (automatic [MaskStrategy.FULL] mask).
 *
 * The extractor recurses into nested data classes, building dot-separated
 * paths (e.g. `"database.credentials.password"`).
 *
 * Example:
 * ```kotlin
 * data class Credentials(
 *     @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 4)
 *     val apiKey: String,
 *     val token: SecretString       // auto-detected, FULL mask
 * )
 *
 * data class AppConfig(
 *     val name: String,
 *     val credentials: Credentials  // nested - will be recursed
 * )
 *
 * val fields = SecretExtractor.extract(AppConfig::class)
 * // fields[0] -> SecretFieldInfo("credentials.apiKey", PARTIAL, 4)
 * // fields[1] -> SecretFieldInfo("credentials.token",  FULL,    0)
 * ```
 *
 * @see SecretFieldInfo
 * @see Secret
 * @see SecretMasker
 * @since 1.0
 */
object SecretExtractor {

    /**
     * Metadata describing a single secret field discovered during extraction.
     *
     * @property path Dot-separated path from the root config class to the
     *   field (e.g. `"database.password"`).
     * @property strategy The [MaskStrategy] to use when displaying this field.
     * @property visibleChars Number of characters to leave visible (relevant
     *   for [MaskStrategy.PARTIAL]).
     * @since 1.0
     */
    data class SecretFieldInfo(
        val path: String,
        val strategy: MaskStrategy,
        val visibleChars: Int
    )

    /**
     * Recursively inspects the primary constructor parameters of [klass]
     * and returns a list of [SecretFieldInfo] for every secret field found.
     *
     * The extraction logic:
     * 1. For each parameter annotated with [@Secret][Secret], a
     *    [SecretFieldInfo] is created using the annotation's [MaskStrategy]
     *    and `visibleChars`.
     * 2. For each parameter of type [SecretString] **without** a [@Secret][Secret]
     *    annotation, a [SecretFieldInfo] is created with
     *    [MaskStrategy.FULL] and `visibleChars = 0`.
     * 3. For each parameter whose type is a `data class`, the method
     *    recurses, prepending the current field name to all discovered paths.
     *
     * If [klass] has no primary constructor, an empty list is returned.
     *
     * @param klass The [KClass] to inspect (typically a data class).
     * @param prefix A dot-separated path prefix for nested calls.
     *   Defaults to `""` (root level).
     * @return A list of [SecretFieldInfo] entries, one per secret field.
     * @see SecretFieldInfo
     * @see SecretMasker.mask
     * @since 1.0
     */
    fun extract(klass: KClass<*>, prefix: String = ""): List<SecretFieldInfo> {
        val result = mutableListOf<SecretFieldInfo>()
        val constructor = klass.primaryConstructor ?: return result

        for (param in constructor.parameters) {
            val name = param.name ?: continue
            val fullPath = if (prefix.isEmpty()) name else "$prefix.$name"
            val secretAnn = param.findAnnotation<Secret>()

            if (secretAnn != null) {
                result.add(SecretFieldInfo(fullPath, secretAnn.mask, secretAnn.visibleChars))
            }

            // SecretString type gets automatic FULL mask if no @Secret annotation
            if (param.type.classifier == SecretString::class && secretAnn == null) {
                result.add(SecretFieldInfo(fullPath, MaskStrategy.FULL, 0))
            }

            // Recurse for nested data classes
            val paramKlass = param.type.classifier as? KClass<*>
            if (paramKlass?.isData == true) {
                result.addAll(this.extract(paramKlass, fullPath))
            }
        }
        return result
    }
}
