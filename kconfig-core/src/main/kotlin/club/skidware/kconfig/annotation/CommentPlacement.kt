package club.skidware.kconfig.annotation

/**
 * Determines the position of a [Comment] relative to its associated configuration field
 * in the serialized YAML output.
 *
 * Usage:
 * ```kotlin
 * @Comment("Port number", placement = CommentPlacement.INLINE)
 * val port: Int = 8080
 * ```
 *
 * @see Comment
 * @since 1.0
 */
enum class CommentPlacement {
    /** Places the comment on the line(s) directly above the field. This is the default placement. */
    ABOVE,
    /** Places the comment on the same line as the field, after the value. */
    INLINE
}
