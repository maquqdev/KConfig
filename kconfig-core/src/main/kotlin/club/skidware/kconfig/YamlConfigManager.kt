package club.skidware.kconfig

import club.skidware.kconfig.error.ConfigErrorCollector
import club.skidware.kconfig.error.ConfigErrorFormatter
import club.skidware.kconfig.migration.ConfigMigration
import club.skidware.kconfig.migration.MigrationRunner
import club.skidware.kconfig.reader.Deserializer
import club.skidware.kconfig.reader.EnvOverrideResolver
import club.skidware.kconfig.reader.YamlReader
import club.skidware.kconfig.serializer.*
import club.skidware.kconfig.watcher.FileWatcher
import club.skidware.kconfig.writer.CommentExtractor
import club.skidware.kconfig.writer.Serializer
import club.skidware.kconfig.writer.YamlWriter
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * The main entry point for KConfig -- a type-safe, annotation-driven YAML
 * configuration manager for Kotlin.
 *
 * `YamlConfigManager` provides a complete lifecycle for managing YAML
 * configuration files backed by Kotlin data classes:
 *
 * 1. **Register custom serializers** for types not natively supported.
 * 2. **Register migrations** to upgrade configuration schemas across versions.
 * 3. **Load** a configuration from a YAML file (creating it with defaults if absent).
 * 4. **Save** a configuration back to YAML with comments and proper formatting.
 * 5. **Reload** a configuration from disk on demand.
 * 6. **Watch** a configuration file for changes and auto-reload.
 *
 * **Full lifecycle example:**
 * ```kotlin
 * // 1. Register custom serializers (optional)
 * YamlConfigManager.registerSerializer(Duration::class, DurationSerializer)
 *
 * // 2. Register migrations (optional)
 * YamlConfigManager.registerMigration(MyConfig::class, MigrateV1ToV2)
 *
 * // 3. Load configuration
 * val config = YamlConfigManager.load<MyConfig>(dataFolder.resolve("config.yml"))
 *
 * // 4. Save configuration
 * YamlConfigManager.save(dataFolder.resolve("config.yml"), config)
 *
 * // 5. Reload on demand
 * val fresh = YamlConfigManager.reload<MyConfig>(dataFolder.resolve("config.yml"))
 *
 * // 6. Watch for file changes
 * YamlConfigManager.watch<MyConfig>(dataFolder.resolve("config.yml")) { newConfig ->
 *     println("Config reloaded: $newConfig")
 * }
 *
 * // 7. Debug output with secret masking
 * println(YamlConfigManager.toDebugString(config))
 *
 * // 8. Cleanup watchers on shutdown
 * YamlConfigManager.stopAllWatchers()
 * ```
 *
 * **Built-in features:**
 * - Automatic default generation from data class constructor defaults
 * - `@Comment` annotations rendered as YAML comments
 * - `@Env` annotations for environment variable overrides
 * - `@Secret` annotations for masking sensitive values in debug output
 * - `@Transient` annotations for excluding fields from serialization
 * - Schema versioning and migration with automatic backups
 * - File watching with debounced auto-reload
 * - Colored error reporting with field-level validation details
 *
 * **Built-in serializers:** The manager automatically registers serializers for
 * common types including [SecretString]. Additional Bukkit serializers are available
 * via [club.skidware.kconfig.bukkit.BukkitSerializers.registerAll].
 *
 * @see ConfigMigration
 * @see FileWatcher
 * @see SerializerRegistry
 * @since 1.0
 */
object YamlConfigManager {

    private val logger = LoggerFactory.getLogger(YamlConfigManager::class.java)

    /**
     * The serializer registry used to resolve [TypeSerializer] implementations
     * for custom types during serialization and deserialization.
     *
     * Built-in serializers are registered automatically at initialization.
     * Use [registerSerializer] to add custom serializers.
     *
     * @see registerSerializer
     * @since 1.0
     */
    val registry = SerializerRegistry()
    private val migrationRunners = ConcurrentHashMap<KClass<*>, MigrationRunner>()
    private val watchers = ConcurrentHashMap<File, FileWatcher>()
    private val errorFormatter = ConfigErrorFormatter(useColors = true)
    @Volatile private var output: PrintStream = System.err

    init {
        BuiltinSerializers.registerAll(this.registry)
        this.registry.register(SecretString::class, SecretStringSerializer)
    }

    /**
     * Registers a custom [TypeSerializer] for the given type [klass].
     *
     * Custom serializers allow non-standard types to be serialized to and
     * deserialized from YAML. Registered serializers take precedence over
     * default data class serialization.
     *
     * Example:
     * ```kotlin
     * YamlConfigManager.registerSerializer(Duration::class, DurationSerializer)
     * ```
     *
     * @param T The type to register a serializer for.
     * @param klass The [KClass] of the type.
     * @param serializer The [TypeSerializer] implementation.
     * @see SerializerRegistry
     * @since 1.0
     */
    fun <T : Any> registerSerializer(klass: KClass<T>, serializer: TypeSerializer<T>) {
        this.registry.register(klass, serializer)
    }

    /**
     * Registers a custom [TypeSerializer] for the reified type [T].
     *
     * This is a convenience overload that infers the [KClass] from the
     * reified type parameter.
     *
     * Example:
     * ```kotlin
     * YamlConfigManager.registerSerializer<Duration>(DurationSerializer)
     * ```
     *
     * @param T The type to register a serializer for.
     * @param serializer The [TypeSerializer] implementation.
     * @see registerSerializer
     * @since 1.0
     */
    inline fun <reified T : Any> registerSerializer(serializer: TypeSerializer<T>) {
        this.registerSerializer(T::class, serializer)
    }

    /**
     * Registers a [ConfigMigration] for the given configuration type [klass].
     *
     * Migrations are applied automatically during [load] when the file's
     * `configVersion` is lower than the target version defined in the data class.
     * Multiple migrations can be registered and will be chained in order.
     *
     * Example:
     * ```kotlin
     * YamlConfigManager.registerMigration(MyConfig::class, MigrateV1ToV2)
     * YamlConfigManager.registerMigration(MyConfig::class, MigrateV2ToV3)
     * ```
     *
     * @param klass The [KClass] of the configuration type the migration applies to.
     * @param migration The migration step to register.
     * @see ConfigMigration
     * @see MigrationRunner
     * @since 1.0
     */
    fun registerMigration(klass: KClass<*>, migration: ConfigMigration) {
        this.migrationRunners.getOrPut(klass) { MigrationRunner() }.register(migration)
    }

    /**
     * Sets the output stream used for error and diagnostic messages.
     *
     * Defaults to [System.err]. Useful for redirecting KConfig output in
     * testing or custom logging scenarios.
     *
     * @param stream The [PrintStream] to use for output.
     * @since 1.0
     */
    fun setOutput(stream: PrintStream) {
        this.output = stream
    }

    /**
     * Loads a configuration of type [T] from the specified [file].
     *
     * This is a convenience overload that infers the [KClass] from the
     * reified type parameter.
     *
     * Example:
     * ```kotlin
     * val config = YamlConfigManager.load<ServerConfig>(dataFolder.resolve("config.yml"))
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to load from. Created with defaults if it does not exist.
     * @return The deserialized and validated configuration instance.
     * @throws IllegalStateException If the config cannot be loaded and no default can be created.
     * @see load
     * @see save
     * @since 1.0
     */
    inline fun <reified T : Any> load(file: File): T {
        return this.load(file, T::class)
    }

    /**
     * Loads a configuration of type [T] from the specified [file].
     *
     * If the file does not exist, it is created with default values derived from [T]'s
     * primary constructor defaults. After loading, the config is validated, errors are
     * reported via [ConfigErrorFormatter], `@Env` overrides are applied, and the file
     * is re-saved to fill in any missing fields.
     *
     * **Loading pipeline:**
     * 1. If the file does not exist, create it with defaults and return.
     * 2. Read the YAML file into a raw map.
     * 3. Run any registered [ConfigMigration]s if the file version is outdated.
     * 4. Deserialize the map into an instance of [T], collecting validation errors.
     * 5. Report errors (if any) via the configured error formatter.
     * 6. Save the instance back to normalize formatting and fill missing defaults.
     * 7. Apply `@Env` environment variable overrides to the in-memory instance.
     *
     * Example:
     * ```kotlin
     * val config = YamlConfigManager.load<ServerConfig>(dataFolder.resolve("config.yml"))
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to load from. Created with defaults if it does not exist.
     * @param klass The [KClass] of the configuration type.
     * @return The deserialized and validated configuration instance.
     * @throws IllegalStateException If the config cannot be loaded and no default can be created.
     * @see save
     * @see reload
     * @since 1.0
     */
    fun <T : Any> load(file: File, klass: KClass<T>): T {
        if (!file.exists()) {
            return this.loadFromDefaults(file, klass)
        }
        var rawMap = YamlReader.read(file).toMutableMap()
        rawMap = this.applyMigrations(rawMap, klass, file)
        val instance = this.deserializeAndReport(rawMap, klass, file)
        this.save(file, instance)
        return EnvOverrideResolver.resolve(instance, klass)
    }

    private fun <T : Any> loadFromDefaults(file: File, klass: KClass<T>): T {
        val default = this.createDefault(klass)
            ?: throw IllegalStateException("Cannot create default instance of ${klass.simpleName}")
        this.save(file, default)
        return EnvOverrideResolver.resolve(default, klass)
    }

    private fun <T : Any> applyMigrations(
        rawMap: MutableMap<String, Any?>,
        klass: KClass<T>,
        file: File
    ): MutableMap<String, Any?> {
        val runner = this.migrationRunners[klass]
        if (runner != null && runner.hasMigrations()) {
            val mutableMap = rawMap.toMutableMap()
            return runner.migrate(mutableMap, this.getTargetVersion(klass), file)
        }
        return rawMap
    }

    private fun <T : Any> deserializeAndReport(
        rawMap: MutableMap<String, Any?>,
        klass: KClass<T>,
        file: File
    ): T {
        val errors = ConfigErrorCollector()
        val deserializer = Deserializer(this.registry)
        val result = deserializer.deserialize(klass, rawMap, errors)

        if (errors.hasErrors()) {
            val report = this.errorFormatter.format(errors.all(), file.name)
            this.output.print(report)
            this.logger.warn("Configuration errors in {}: {}", file.name, report.trim())
        }

        return result ?: this.createDefault(klass)
            ?: throw IllegalStateException("Failed to load config from ${file.name} and cannot create default")
    }

    /**
     * Saves a configuration [instance] to the specified [file] in YAML format.
     *
     * The instance is serialized using [Serializer], `@Comment` annotations are
     * extracted via [CommentExtractor], and the output is written by [YamlWriter].
     * Parent directories are created automatically if they do not exist.
     *
     * Example:
     * ```kotlin
     * val config = ServerConfig(host = "0.0.0.0", port = 9090)
     * YamlConfigManager.save(dataFolder.resolve("config.yml"), config)
     * ```
     *
     * @param T The configuration type.
     * @param file The target YAML file to write to.
     * @param instance The configuration instance to serialize and save.
     * @see load
     * @see Serializer
     * @see YamlWriter
     * @since 1.0
     */
    fun <T : Any> save(file: File, instance: T) {
        val klass = instance::class
        val data = Serializer.serialize(instance, this.registry)
        val comments = CommentExtractor.extract(klass)
        YamlWriter.write(file, data, comments)
    }

    /**
     * Reloads a configuration of type [T] from the specified [file].
     *
     * This is functionally equivalent to calling [load] again. It re-reads
     * the file from disk, applies migrations, validates, and resolves `@Env`
     * overrides.
     *
     * Example:
     * ```kotlin
     * val freshConfig = YamlConfigManager.reload<ServerConfig>(dataFolder.resolve("config.yml"))
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to reload from.
     * @return The freshly deserialized configuration instance.
     * @throws IllegalStateException If the config cannot be loaded and no default can be created.
     * @see load
     * @since 1.0
     */
    inline fun <reified T : Any> reload(file: File): T {
        return this.load(file, T::class)
    }

    /**
     * Watches a configuration [file] for changes and invokes [onReload] with the
     * freshly loaded configuration whenever the file is modified.
     *
     * If a watcher already exists for the given file, the previous watcher is
     * stopped and replaced. The returned [FileWatcher] is started automatically.
     *
     * The reload callback is debounced (default 500ms) to avoid redundant
     * reloads during rapid successive writes. Errors during reload are caught
     * and printed to the configured output stream.
     *
     * Example:
     * ```kotlin
     * val watcher = YamlConfigManager.watch(configFile, ServerConfig::class) { config ->
     *     println("Config reloaded: port=${config.port}")
     * }
     *
     * // Later, to stop:
     * watcher.stop()
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to watch.
     * @param klass The [KClass] of the configuration type.
     * @param onReload Callback invoked with the reloaded configuration instance.
     * @return The started [FileWatcher] instance.
     * @see stopWatching
     * @see stopAllWatchers
     * @see FileWatcher
     * @since 1.0
     */
    fun <T : Any> watch(file: File, klass: KClass<T>, onReload: (T) -> Unit): FileWatcher {
        val watcher = FileWatcher(file) {
            try {
                val reloaded = this.load(file, klass)
                onReload(reloaded)
            } catch (e: Exception) {
                this.output.println("[KConfig] Reload failed for ${file.name}: ${e.message}")
                this.logger.error("Reload failed for {}: {}", file.name, e.message, e)
            }
        }
        this.watchers.compute(file) { _, existing ->
            existing?.stop()
            watcher
        }
        watcher.start()
        return watcher
    }

    /**
     * Watches a configuration [file] for changes and invokes [onReload] with the
     * freshly loaded configuration whenever the file is modified.
     *
     * This is a convenience overload that infers the [KClass] from the
     * reified type parameter.
     *
     * Example:
     * ```kotlin
     * YamlConfigManager.watch<ServerConfig>(configFile) { config ->
     *     println("Config reloaded: port=${config.port}")
     * }
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to watch.
     * @param onReload Callback invoked with the reloaded configuration instance.
     * @return The started [FileWatcher] instance.
     * @see watch
     * @see stopWatching
     * @since 1.0
     */
    inline fun <reified T : Any> watch(file: File, noinline onReload: (T) -> Unit): FileWatcher {
        return this.watch(file, T::class, onReload)
    }

    /**
     * Creates a live [ConfigRef] for the given configuration type and file.
     *
     * A `ConfigRef` is a reload-safe wrapper: any class holding a `ConfigRef`
     * always sees the latest config after a reload, without re-injection.
     *
     * ```kotlin
     * // In plugin onEnable:
     * val configRef = YamlConfigManager.ref<DuelsConfig>(dataFolder.resolve("config.yml"))
     *     .withAutoReload()
     *     .onChange { old, new -> logger.info("Config reloaded") }
     *
     * // Pass to managers — they never hold stale references:
     * val duelManager = DuelManager(configRef)
     * val queueManager = QueueManager(configRef)
     * ```
     *
     * @param T The configuration data class type.
     * @param file The YAML file to load and track.
     * @return A [ConfigRef] wrapping the loaded config.
     * @see ConfigRef
     * @since 1.0
     */
    inline fun <reified T : Any> ref(file: File): ConfigRef<T> {
        return ConfigRef(file, T::class)
    }

    /**
     * Stops watching the specified [file] for changes.
     *
     * If no watcher is active for the given file, this method is a no-op.
     *
     * @param file The file to stop watching.
     * @see watch
     * @see stopAllWatchers
     * @since 1.0
     */
    fun stopWatching(file: File) {
        this.watchers.remove(file)?.stop()
    }

    /**
     * Stops all active file watchers and clears the watcher registry.
     *
     * This should be called during application shutdown to release resources
     * and terminate watcher daemon threads.
     *
     * Example:
     * ```kotlin
     * // In plugin onDisable or application shutdown hook:
     * YamlConfigManager.stopAllWatchers()
     * ```
     *
     * @see watch
     * @see stopWatching
     * @since 1.0
     */
    fun stopAllWatchers() {
        this.watchers.values.forEach { it.stop() }
        this.watchers.clear()
    }

    /**
     * Produces a YAML-formatted debug string of the configuration [instance]
     * with secret values masked.
     *
     * Properties annotated with `@Secret` are masked according to their
     * configured [MaskStrategy][club.skidware.kconfig.annotation.MaskStrategy]
     * (e.g., `"s]3cr3t"` becomes `"s***"` with `PARTIAL` strategy).
     * All other values are rendered normally with comments.
     *
     * This is safe to use in logging and diagnostic output without risking
     * secret leakage.
     *
     * Example:
     * ```kotlin
     * data class DbConfig(
     *     val host: String = "localhost",
     *     @Secret val password: SecretString = SecretString("s3cr3t")
     * )
     *
     * val debug = YamlConfigManager.toDebugString(DbConfig())
     * // Output:
     * // host: localhost
     * // password: "********"
     * ```
     *
     * @param T The configuration type.
     * @param instance The configuration instance to render.
     * @return A YAML-formatted string with secrets masked.
     * @see club.skidware.kconfig.annotation.Secret
     * @see club.skidware.kconfig.serializer.SecretMasker
     * @since 1.0
     */
    fun <T : Any> toDebugString(instance: T): String {
        val klass = instance::class
        val data = Serializer.serialize(instance, this.registry).toMutableMap()
        val secrets = SecretExtractor.extract(klass)

        // Mask secrets
        for (secret in secrets) {
            this.maskInMap(data, secret.path, secret.strategy, secret.visibleChars)
        }

        val comments = CommentExtractor.extract(klass)
        return YamlWriter.writeToString(data, comments)
    }

    @Suppress("UNCHECKED_CAST")
    private fun maskInMap(map: MutableMap<String, Any?>, path: String, strategy: club.skidware.kconfig.annotation.MaskStrategy, visibleChars: Int) {
        val parts = path.split('.')
        var current: MutableMap<String, Any?> = map

        for (i in 0 until parts.size - 1) {
            current = (current[parts[i]] as? MutableMap<String, Any?>) ?: return
        }

        val lastKey = parts.last()
        val value = current[lastKey]
        if (value is String) {
            current[lastKey] = SecretMasker.mask(value, strategy, visibleChars)
        } else if (value != null) {
            current[lastKey] = "********"
        }
    }

    private fun <T : Any> createDefault(klass: KClass<T>): T? {
        val constructor = klass.primaryConstructor ?: return null
        return try {
            if (constructor.parameters.all { it.isOptional }) {
                constructor.callBy(emptyMap())
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getTargetVersion(klass: KClass<*>): Int {
        val constructor = klass.primaryConstructor ?: return 1
        val versionParam = constructor.parameters.find { it.name == "configVersion" }
        if (versionParam != null) {
            // Try to get default value by creating default instance
            val default = this.createDefault(klass) ?: return 1
            val prop = klass.members.find { it.name == "configVersion" }
            return (prop?.call(default) as? Number)?.toInt() ?: 1
        }
        return 1
    }
}
