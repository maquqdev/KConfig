package club.skidware.kconfig.error

/**
 * Renders a list of [ConfigError] instances into a human-readable, optionally ANSI-colored report.
 *
 * Errors are grouped by their concrete type (e.g. `InvalidValue`, `UnknownKey`) and printed
 * with their dot-separated paths highlighted. A trailing summary line counts how many fields
 * fell back to default values due to [ConfigError.OutOfRange] or [ConfigError.PatternMismatch].
 *
 * Example output (with colors disabled for illustration):
 * ```
 * 3 config error(s) found in my-service:
 *
 *   [InvalidValue]
 *     server.port: Invalid value 'abc', expected Int
 *
 *   [UnknownKey]
 *     databse: Unknown key 'databse' - did you mean 'database'?
 *
 *   [OutOfRange]
 *     server.maxThreads: Value 9999 is out of range [1.0, 512.0], fell back to 16
 *
 *   1 field(s) fell back to default values.
 * ```
 *
 * Usage:
 * ```kotlin
 * val formatter = ConfigErrorFormatter(useColors = true)
 * val report = formatter.format(collector.all(), configName = "my-service")
 * System.err.print(report)
 * ```
 *
 * @param useColors When `true` (default), wraps error categories, paths, and summaries in
 *                  ANSI escape codes. Set to `false` for log files or non-terminal output.
 * @see ConfigError
 * @see ConfigErrorCollector
 * @since 1.0
 */
class ConfigErrorFormatter(private val useColors: Boolean = true) {

    /**
     * Formats the given list of errors into a multi-line diagnostic report.
     *
     * If [errors] is empty, returns an empty string immediately. Otherwise, the report begins
     * with a count header, followed by errors grouped by type, and ends with a fallback summary
     * when applicable.
     *
     * @param errors The list of [ConfigError] instances to render.
     * @param configName A label for the configuration source, included in the header line.
     *                   Defaults to `"config"`.
     * @return A formatted, potentially ANSI-colored string suitable for terminal output.
     */
    fun format(errors: List<ConfigError>, configName: String = "config"): String {
        if (errors.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine(this.colored("${errors.size} config error(s) found in $configName:", YELLOW))
        sb.appendLine()

        val grouped = errors.groupBy { it::class.simpleName ?: "Unknown" }
        for ((type, group) in grouped) {
            sb.appendLine(this.colored("  [$type]", RED))
            for (error in group) {
                sb.appendLine("    ${this.colored(error.path, CYAN)}: ${error.message}")
            }
            sb.appendLine()
        }

        val fallbackCount = errors.count { it is ConfigError.OutOfRange || it is ConfigError.PatternMismatch }
        if (fallbackCount > 0) {
            sb.appendLine(this.colored("  $fallbackCount field(s) fell back to default values.", YELLOW))
        }

        return sb.toString()
    }

    /**
     * Conditionally wraps [text] in ANSI escape codes.
     *
     * @param text The text to colorize.
     * @param color The ANSI color code to apply.
     * @return The original text if [useColors] is `false`, otherwise the text wrapped in color codes.
     */
    private fun colored(text: String, color: String): String =
        if (this.useColors) "$color$text$RESET" else text

    /**
     * ANSI escape code constants used for terminal coloring.
     */
    companion object {
        private const val RESET = "\u001B[0m"
        private const val RED = "\u001B[31m"
        private const val YELLOW = "\u001B[33m"
        private const val CYAN = "\u001B[36m"
    }
}
