package club.skidware.kconfig.annotation

/**
 * Marks a configuration field with a descriptive comment that will be written into the YAML output.
 *
 * Comments improve readability for administrators editing config files manually.
 * Supports both above-line and inline placement strategies via [CommentPlacement].
 * Multiple lines can be provided, each rendered as a separate comment line.
 *
 * Usage:
 * ```kotlin
 * data class ServerConfig(
 *     @Comment("The unique identifier for this server instance")
 *     val serverId: String = "lobby01",
 *
 *     @Comment("Maximum concurrent players", placement = CommentPlacement.INLINE)
 *     val maxPlayers: Int = 200,
 *
 *     @Comment("Enable debug logging", "Warning: generates verbose output")
 *     val debug: Boolean = false
 * )
 * ```
 *
 * @property lines One or more comment lines to include in the YAML output.
 * @property placement Controls whether the comment appears above the field or inline. Defaults to [CommentPlacement.ABOVE].
 * @see CommentPlacement
 * @since 1.0
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Comment(
    vararg val lines: String,
    val placement: CommentPlacement = CommentPlacement.ABOVE
)
