package club.skidware.kconfig

import club.skidware.kconfig.annotation.MaskStrategy
import club.skidware.kconfig.annotation.Secret
import club.skidware.kconfig.serializer.SecretExtractor
import club.skidware.kconfig.serializer.SecretMasker
import club.skidware.kconfig.serializer.SecretString
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ---- Test data classes for secrets ----

data class SecretFields(
    val name: String = "public",
    @Secret(mask = MaskStrategy.FULL) val password: String = "mypassword",
    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 4) val token: String = "abcdef123456"
)

data class AutoSecretFields(
    val label: String = "visible",
    val apiKey: SecretString = SecretString("auto-detected-key")
)

data class InnerSecrets(
    @Secret val innerSecret: String = "nested-secret"
)

data class OuterSecrets(
    val name: String = "outer",
    val nested: InnerSecrets = InnerSecrets()
)

data class DebugConfig(
    val host: String = "localhost",
    @Secret(mask = MaskStrategy.FULL) val password: String = "s3cret",
    val apiKey: SecretString = SecretString("key-12345")
)

class SecretTest {

    @Nested
    inner class SecretStringBehavior {

        @Test
        fun `toString always returns masked value`() {
            val secret = SecretString("my-secret-value")
            assertEquals("********", secret.toString())
        }

        @Test
        fun `expose returns actual value`() {
            val secret = SecretString("my-secret-value")
            assertEquals("my-secret-value", secret.expose())
        }
    }

    @Nested
    inner class MaskStrategies {

        @Test
        fun `FULL strategy masks entire value`() {
            val result = SecretMasker.mask("anything", MaskStrategy.FULL, 0)
            assertEquals("********", result)
        }

        @Test
        fun `PARTIAL with 4 visible chars`() {
            val result = SecretMasker.mask("mypassword123", MaskStrategy.PARTIAL, 4)
            assertEquals("mypa********", result)
        }

        @Test
        fun `EDGES shows first and last character`() {
            val result = SecretMasker.mask("apikey999", MaskStrategy.EDGES, 0)
            assertEquals("a*******9", result)
        }

        @Test
        fun `empty string returns masked for FULL`() {
            val result = SecretMasker.mask("", MaskStrategy.FULL, 0)
            assertEquals("********", result)
        }

        @Test
        fun `empty string returns masked for PARTIAL`() {
            val result = SecretMasker.mask("", MaskStrategy.PARTIAL, 4)
            assertEquals("********", result)
        }

        @Test
        fun `empty string returns masked for EDGES`() {
            val result = SecretMasker.mask("", MaskStrategy.EDGES, 0)
            assertEquals("********", result)
        }

        @Test
        fun `short string 2 chars with EDGES returns masked`() {
            val result = SecretMasker.mask("ab", MaskStrategy.EDGES, 0)
            assertEquals("********", result)
        }
    }

    @Nested
    inner class SecretExtraction {

        @Test
        fun `extracts Secret annotated fields from data class`() {
            val secrets = SecretExtractor.extract(SecretFields::class)

            assertEquals(2, secrets.size)
            assertTrue(secrets.any { it.path == "password" && it.strategy == MaskStrategy.FULL })
            assertTrue(secrets.any { it.path == "token" && it.strategy == MaskStrategy.PARTIAL && it.visibleChars == 4 })
        }

        @Test
        fun `detects SecretString fields automatically without annotation`() {
            val secrets = SecretExtractor.extract(AutoSecretFields::class)

            assertEquals(1, secrets.size)
            assertEquals("apiKey", secrets[0].path)
            assertEquals(MaskStrategy.FULL, secrets[0].strategy)
        }

        @Test
        fun `recurses into nested data classes`() {
            val secrets = SecretExtractor.extract(OuterSecrets::class)

            assertEquals(1, secrets.size)
            assertEquals("nested.innerSecret", secrets[0].path)
            assertEquals(MaskStrategy.FULL, secrets[0].strategy)
        }
    }

    @Nested
    inner class DebugStringMasking {

        @Test
        fun `toDebugString masks secret fields`() {
            val config = DebugConfig(
                host = "prod.example.com",
                password = "super-secret-pass",
                apiKey = SecretString("key-12345")
            )

            val debugStr = YamlConfigManager.toDebugString(config)

            // host should be visible
            assertTrue(debugStr.contains("prod.example.com"))
            // password should be masked (FULL)
            assertTrue(debugStr.contains("********"))
            // The actual password should NOT appear
            assertTrue(!debugStr.contains("super-secret-pass"))
            // The actual api key should NOT appear
            assertTrue(!debugStr.contains("key-12345"))
        }
    }
}
