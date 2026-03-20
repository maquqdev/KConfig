package club.skidware.kconfig

import club.skidware.kconfig.error.closestMatch
import club.skidware.kconfig.error.levenshtein
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevenshteinTest {

    @Nested
    inner class DistanceCalculation {

        @Test
        fun `same string returns 0`() {
            assertEquals(0, "hello".levenshtein("hello"))
        }

        @Test
        fun `empty strings return 0`() {
            assertEquals(0, "".levenshtein(""))
        }

        @Test
        fun `one char difference returns 1`() {
            assertEquals(1, "cat".levenshtein("bat"))
        }

        @Test
        fun `insertion gives distance 1`() {
            assertEquals(1, "cat".levenshtein("cats"))
        }

        @Test
        fun `deletion gives distance 1`() {
            assertEquals(1, "cats".levenshtein("cat"))
        }

        @Test
        fun `completely different strings give high distance`() {
            val dist = "abc".levenshtein("xyz")
            assertTrue(dist >= 3, "Completely different strings should have distance >= 3, got $dist")
        }

        @Test
        fun `one empty string returns length of the other`() {
            assertEquals(5, "".levenshtein("hello"))
            assertEquals(5, "hello".levenshtein(""))
        }
    }

    @Nested
    inner class ClosestMatch {

        @Test
        fun `finds best match within maxDistance`() {
            val candidates = listOf("name", "count", "enabled", "port")
            val result = candidates.closestMatch("naem")

            assertEquals("name", result)
        }

        @Test
        fun `returns null when all candidates are too far`() {
            val candidates = listOf("alpha", "beta", "gamma")
            val result = candidates.closestMatch("zzzzzzzzz", maxDistance = 2)

            assertNull(result)
        }

        @Test
        fun `returns exact match when present`() {
            val candidates = listOf("foo", "bar", "baz")
            val result = candidates.closestMatch("foo")

            assertEquals("foo", result)
        }

        @Test
        fun `empty candidates returns null`() {
            val candidates = emptyList<String>()
            val result = candidates.closestMatch("anything")

            assertNull(result)
        }

        @Test
        fun `picks closest among multiple close matches`() {
            val candidates = listOf("test", "text", "tent")
            val result = candidates.closestMatch("tset")

            assertEquals("test", result)
        }
    }
}
