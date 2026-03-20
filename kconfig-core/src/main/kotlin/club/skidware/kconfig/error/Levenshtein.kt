package club.skidware.kconfig.error

/**
 * Computes the [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
 * between this string and [other].
 *
 * The Levenshtein distance is the minimum number of single-character edits (insertions,
 * deletions, or substitutions) required to transform one string into the other. This
 * implementation uses the classic Wagner-Fischer dynamic-programming algorithm with
 * O(m * n) time and space complexity, where m and n are the lengths of the two strings.
 *
 * Used internally by [closestMatch] to power "did you mean ...?" suggestions in
 * [ConfigError.UnknownKey].
 *
 * Examples:
 * ```kotlin
 * "kitten".levenshtein("sitting")  // 3
 * "host".levenshtein("hots")      // 2
 * "abc".levenshtein("abc")        // 0
 * "".levenshtein("hello")         // 5
 * ```
 *
 * @param other The target string to compare against.
 * @return The edit distance as a non-negative integer. Returns `0` when the strings are identical.
 * @see closestMatch
 * @since 1.0
 */
fun String.levenshtein(other: String): Int {
    val m = this.length
    val n = other.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (this[i - 1] == other[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
    }
    return dp[m][n]
}

/**
 * Finds the element in this collection that is closest to [input] by
 * [Levenshtein distance][String.levenshtein], provided the distance does not exceed [maxDistance].
 *
 * This is used by the [Deserializer][club.skidware.kconfig.reader.Deserializer] to suggest
 * corrections for unknown YAML keys. When multiple candidates tie at the same distance,
 * the first one encountered (iteration order) is returned.
 *
 * Examples:
 * ```kotlin
 * val keys = setOf("database", "server", "logging")
 *
 * keys.closestMatch("databse")          // "database" (distance 1)
 * keys.closestMatch("srvr")             // "server"   (distance 2)
 * keys.closestMatch("completelyWrong")  // null       (distance > 3)
 * keys.closestMatch("srvr", maxDistance = 1) // null   (distance 2 > maxDistance 1)
 * ```
 *
 * @param input The string to match against elements in this collection.
 * @param maxDistance The maximum Levenshtein distance to accept. Candidates with a greater
 *                   distance are discarded. Defaults to `3`.
 * @return The closest matching string, or `null` if no element is within [maxDistance].
 * @see String.levenshtein
 * @see ConfigError.UnknownKey
 * @since 1.0
 */
fun Collection<String>.closestMatch(input: String, maxDistance: Int = 3): String? {
    return this.map { it to input.levenshtein(it) }
        .filter { it.second <= maxDistance }
        .minByOrNull { it.second }
        ?.first
}
