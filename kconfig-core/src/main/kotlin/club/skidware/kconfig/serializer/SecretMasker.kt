package club.skidware.kconfig.serializer

import club.skidware.kconfig.annotation.MaskStrategy

/**
 * Utility object that masks secret values for safe display in logs,
 * diagnostics, and configuration dumps.
 *
 * Three masking strategies are supported (see [MaskStrategy]):
 *
 * | Strategy | Input | Output | Description |
 * |----------|-------|--------|-------------|
 * | [MaskStrategy.FULL] | `"superSecret"` | `"********"` | Replaces the entire value with asterisks. |
 * | [MaskStrategy.PARTIAL] | `"superSecret"` (visibleChars=4) | `"supe********"` | Keeps the first N characters visible. |
 * | [MaskStrategy.EDGES] | `"superSecret"` | `"s**********t"` | Shows only the first and last character. |
 *
 * Empty strings always produce `"********"` regardless of strategy.
 * Strings of length 2 or less under [MaskStrategy.EDGES] also produce
 * `"********"` to avoid revealing the full value.
 *
 * @see MaskStrategy
 * @see SecretString
 * @see SecretExtractor
 * @since 1.0
 */
object SecretMasker {

    /**
     * Masks a secret value according to the specified [strategy].
     *
     * Example:
     * ```kotlin
     * SecretMasker.mask("mypassword", MaskStrategy.PARTIAL, 4) // "mypa********"
     * SecretMasker.mask("apikey999", MaskStrategy.EDGES, 0)    // "a*******9"
     * SecretMasker.mask("secret", MaskStrategy.FULL, 0)        // "********"
     * SecretMasker.mask("", MaskStrategy.FULL, 0)              // "********"
     * ```
     *
     * @param value The plaintext value to mask.
     * @param strategy The masking strategy to apply.
     * @param visibleChars Number of leading characters to leave visible
     *   (only used by [MaskStrategy.PARTIAL]).
     * @return The masked string representation.
     * @see MaskStrategy
     * @since 1.0
     */
    fun mask(value: String, strategy: MaskStrategy, visibleChars: Int): String {
        if (value.isEmpty()) return "********"

        return when (strategy) {
            MaskStrategy.FULL -> "********"

            MaskStrategy.PARTIAL -> {
                val visible = value.take(visibleChars.coerceAtMost(value.length))
                "$visible${"*".repeat(8)}"
            }

            MaskStrategy.EDGES -> {
                if (value.length <= 2) return "********"
                val first = value.first()
                val last = value.last()
                "$first${"*".repeat((value.length - 2).coerceAtLeast(6))}$last"
            }
        }
    }
}
