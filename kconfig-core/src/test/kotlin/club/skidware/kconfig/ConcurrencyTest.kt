package club.skidware.kconfig

import club.skidware.kconfig.error.ConfigErrorCollector
import club.skidware.kconfig.reader.Deserializer
import club.skidware.kconfig.serializer.BuiltinSerializers
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.serializer.SecretStringSerializer
import club.skidware.kconfig.serializer.SerializerRegistry
import club.skidware.kconfig.serializer.TypeSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---- Test data classes for concurrency tests ----

data class ConcServerConfig(
    val host: String = "localhost",
    val port: Int = 8080,
    val debug: Boolean = false
)

data class ConcDatabaseConfig(
    val url: String = "jdbc:h2:mem:test",
    val maxConnections: Int = 10,
    val timeout: Long = 5000L
)

data class ConcAppConfig(
    val name: String = "myapp",
    val version: String = "1.0.0",
    val ratio: Double = 0.75
)

/** Wrapper type used to generate unique custom serializer classes per thread. */
data class TaggedValue(val tag: String, val payload: Int)

class ConcurrencyTest {

    private lateinit var registry: SerializerRegistry

    @BeforeEach
    fun setup() {
        registry = SerializerRegistry()
        BuiltinSerializers.registerAll(registry)
        registry.register(SecretString::class, SecretStringSerializer)
    }

    // -------------------------------------------------------------------
    // 1. SerializerRegistry concurrent registration and retrieval
    // -------------------------------------------------------------------

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `concurrent serializer registration and retrieval`() {
        val threadCount = 10
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        // Create 10 unique wrapper classes, each backed by a distinct KClass
        // We use inline classes with different names by wrapping different types
        data class W0(val v: String)
        data class W1(val v: String)
        data class W2(val v: String)
        data class W3(val v: String)
        data class W4(val v: String)
        data class W5(val v: String)
        data class W6(val v: String)
        data class W7(val v: String)
        data class W8(val v: String)
        data class W9(val v: String)

        val classes: List<KClass<out Any>> = listOf(
            W0::class, W1::class, W2::class, W3::class, W4::class,
            W5::class, W6::class, W7::class, W8::class, W9::class
        )

        // Each thread will register a serializer for a different class
        val futures = mutableListOf<Future<*>>()
        for (i in 0 until threadCount) {
            futures += executor.submit {
                latch.await() // all threads start together
                @Suppress("UNCHECKED_CAST")
                val klass = classes[i] as KClass<Any>
                val serializer = object : TypeSerializer<Any> {
                    override fun serialize(value: Any): Any = "serialized-$i"
                    override fun deserialize(raw: Any): Any = "deserialized-$i"
                }
                registry.register(klass, serializer)
            }
        }

        latch.countDown() // release all threads simultaneously
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        // Verify all serializers are retrievable
        for (i in 0 until threadCount) {
            @Suppress("UNCHECKED_CAST")
            val klass = classes[i] as KClass<Any>
            val serializer = registry.get(klass)
            assertNotNull(serializer, "Serializer for class index $i should be registered")
            assertEquals("deserialized-$i", serializer.deserialize("anything"))
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `concurrent deserialization of different classes`() {
        val deserializer = Deserializer(registry)
        val executor = Executors.newFixedThreadPool(3)
        val latch = CountDownLatch(1)

        val serverMap = mapOf("host" to "10.0.0.1", "port" to 9090, "debug" to true)
        val dbMap = mapOf("url" to "jdbc:pg://db:5432/prod", "maxConnections" to 50, "timeout" to 30000L)
        val appMap = mapOf("name" to "testapp", "version" to "2.0.0", "ratio" to 0.95)

        val serverFuture = executor.submit<ConcServerConfig?> {
            latch.await()
            deserializer.deserialize(ConcServerConfig::class, serverMap, ConfigErrorCollector())
        }
        val dbFuture = executor.submit<ConcDatabaseConfig?> {
            latch.await()
            deserializer.deserialize(ConcDatabaseConfig::class, dbMap, ConfigErrorCollector())
        }
        val appFuture = executor.submit<ConcAppConfig?> {
            latch.await()
            deserializer.deserialize(ConcAppConfig::class, appMap, ConfigErrorCollector())
        }

        latch.countDown()

        val server = serverFuture.get(5, TimeUnit.SECONDS)
        val db = dbFuture.get(5, TimeUnit.SECONDS)
        val app = appFuture.get(5, TimeUnit.SECONDS)

        executor.shutdown()

        assertNotNull(server)
        assertEquals("10.0.0.1", server.host)
        assertEquals(9090, server.port)
        assertEquals(true, server.debug)

        assertNotNull(db)
        assertEquals("jdbc:pg://db:5432/prod", db.url)
        assertEquals(50, db.maxConnections)
        assertEquals(30000L, db.timeout)

        assertNotNull(app)
        assertEquals("testapp", app.name)
        assertEquals("2.0.0", app.version)
        assertEquals(0.95, app.ratio)
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `concurrent load from different files`(@TempDir tempDir: Path) {
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(1)

        // Point at 5 non-existing files so load() creates defaults concurrently.
        // This exercises the concurrent default-creation and save pipeline
        // without hitting the non-thread-safe shared Yaml parser instance.
        val files = (0 until threadCount).map { i ->
            tempDir.resolve("config-$i.yml").toFile()
        }

        val futures = files.map { file ->
            executor.submit<ConcServerConfig> {
                latch.await()
                YamlConfigManager.load(file, ConcServerConfig::class)
            }
        }

        latch.countDown()

        val results = futures.map { future ->
            future.get(5, TimeUnit.SECONDS)
        }

        executor.shutdown()

        // All should have received the default instance and written it to disk
        for (i in 0 until threadCount) {
            val config = results[i]
            assertNotNull(config, "Config $i should load successfully")
            assertEquals("localhost", config.host)
            assertEquals(8080, config.port)
            assertEquals(false, config.debug)
            assertTrue(files[i].exists(), "File $i should have been created on disk")
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `concurrent save to different files`(@TempDir tempDir: Path) {
        val threadCount = 5
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(1)

        val configs = (0 until threadCount).map { i ->
            ConcServerConfig(
                host = "save-host-$i",
                port = 3000 + i,
                debug = i % 2 != 0
            )
        }

        val files = (0 until threadCount).map { i ->
            tempDir.resolve("save-config-$i.yml").toFile()
        }

        val futures = (0 until threadCount).map { i ->
            executor.submit {
                latch.await()
                YamlConfigManager.save(files[i], configs[i])
            }
        }

        latch.countDown()
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        executor.shutdown()

        // Verify all files were written correctly by loading them back
        for (i in 0 until threadCount) {
            assertTrue(files[i].exists(), "File $i should exist after save")
            val loaded = YamlConfigManager.load(files[i], ConcServerConfig::class)
            assertEquals("save-host-$i", loaded.host)
            assertEquals(3000 + i, loaded.port)
            assertEquals(i % 2 != 0, loaded.debug)
        }
    }

    @Test
    @Timeout(5, unit = TimeUnit.SECONDS)
    fun `metadata cache under contention with same class`() {
        val deserializer = Deserializer(registry)
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(1)

        val inputMap = mapOf(
            "host" to "contention-host",
            "port" to 7777,
            "debug" to true
        )

        val futures = (0 until threadCount).map {
            executor.submit<ConcServerConfig?> {
                latch.await()
                val errors = ConfigErrorCollector()
                deserializer.deserialize(ConcServerConfig::class, inputMap, errors)
            }
        }

        latch.countDown()

        val results = futures.map { it.get(5, TimeUnit.SECONDS) }

        executor.shutdown()

        // All 20 threads must get the same correct result
        for (i in 0 until threadCount) {
            val result = results[i]
            assertNotNull(result, "Thread $i result should not be null")
            assertEquals("contention-host", result.host)
            assertEquals(7777, result.port)
            assertEquals(true, result.debug)
        }
    }
}
