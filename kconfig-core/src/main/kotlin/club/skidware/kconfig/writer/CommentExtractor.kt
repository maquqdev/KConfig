package club.skidware.kconfig.writer

import club.skidware.kconfig.annotation.Comment
import club.skidware.kconfig.annotation.CommentPlacement
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Holds the extracted comment metadata for a single configuration property.
 *
 * Each instance contains the comment text lines and the desired placement
 * strategy (inline or above the property).
 *
 * Example:
 * ```kotlin
 * val comment = CommentData(
 *     lines = listOf("The server port to bind to"),
 *     placement = CommentPlacement.INLINE
 * )
 * // Produces YAML: port: 8080  # The server port to bind to
 * ```
 *
 * @property lines The individual lines of the comment text.
 * @property placement Where the comment should be rendered relative to the property.
 * @see Comment
 * @see CommentPlacement
 * @since 1.0
 */
data class CommentData(
    val lines: List<String>,
    val placement: CommentPlacement
)

/**
 * Extracts `@Comment` annotations from data class constructor parameters and builds
 * a dot-separated path map of comment metadata.
 *
 * This object recursively walks the primary constructor of a configuration data class,
 * collecting any [Comment] annotations and mapping them to their fully qualified
 * dot-notation paths (e.g., `"database.connection.host"`).
 *
 * Example:
 * ```kotlin
 * data class DatabaseConfig(
 *     @Comment(["The database host"], placement = CommentPlacement.INLINE)
 *     val host: String = "localhost",
 *     @Comment(["The database port"])
 *     val port: Int = 5432
 * )
 *
 * data class AppConfig(
 *     val database: DatabaseConfig = DatabaseConfig()
 * )
 *
 * val comments = CommentExtractor.extract(AppConfig::class)
 * // Returns:
 * // {
 * //   "database.host" -> CommentData(["The database host"], INLINE),
 * //   "database.port" -> CommentData(["The database port"], ABOVE)
 * // }
 * ```
 *
 * @see CommentData
 * @see Comment
 * @see YamlWriter
 * @since 1.0
 */
object CommentExtractor {

    /**
     * Recursively extracts [Comment] annotations from the primary constructor
     * parameters of [klass] and all nested data classes.
     *
     * The returned map uses dot-separated paths as keys (e.g., `"server.port"`)
     * and [CommentData] as values containing the comment lines and placement.
     *
     * @param klass The [KClass] to extract comments from. Must be a data class
     *   with a primary constructor for meaningful results.
     * @param prefix The dot-separated path prefix for nested extraction.
     *   Defaults to `""` (root level). Callers typically do not set this;
     *   it is used internally during recursion.
     * @return A map of dot-separated property paths to their [CommentData].
     *   Returns an empty map if [klass] has no primary constructor or no
     *   annotated parameters.
     * @see CommentData
     * @since 1.0
     */
    fun extract(klass: KClass<*>, prefix: String = ""): Map<String, CommentData> {
        val result = mutableMapOf<String, CommentData>()
        val constructor = klass.primaryConstructor ?: return result

        for (param in constructor.parameters) {
            val name = param.name ?: continue
            val fullPath = if (prefix.isEmpty()) name else "$prefix.$name"

            val commentAnn = param.findAnnotation<Comment>()
            if (commentAnn != null) {
                result[fullPath] = CommentData(
                    lines = commentAnn.lines.toList(),
                    placement = commentAnn.placement
                )
            }

            val paramKlass = param.type.classifier as? KClass<*>
            if (paramKlass?.isData == true) {
                result.putAll(this.extract(paramKlass, fullPath))
            }
        }
        return result
    }
}
