package club.skidware.kconfig

import club.skidware.kconfig.watcher.FileWatcher
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * A live, reload-safe reference to a configuration instance of type [T].
 *
 * `ConfigRef` solves the stale-reference problem: when a config is reloaded,
 * every class that holds a `ConfigRef` automatically sees the new values
 * without re-injection or manual wiring.
 *
 * **Three ways to access the current config:**
 * ```kotlin
 * class DuelManager(private val config: ConfigRef<DuelsConfig>) {
 *
 *     // 1. Invoke operator — concise
 *     fun getPort() = config().server.port
 *
 *     // 2. Property access — readable
 *     fun getHost() = config.current.server.host
 *
 *     // 3. Kotlin delegate — zero-boilerplate
 *     private val cfg: DuelsConfig by config
 *     fun getMode() = cfg.game.defaultMode
 * }
 * ```
 *
 * **Selecting sub-sections as delegates:**
 * ```kotlin
 * class QueueManager(config: ConfigRef<DuelsConfig>) {
 *     private val messages by config.selecting { it.messages }
 *     private val cooldowns by config.selecting { it.commandCooldowns }
 *
 *     fun onJoin(player: Player) {
 *         player.sendMessage(messages.prefix + messages.queueJoin)
 *         // messages is always the latest value — never stale
 *     }
 * }
 * ```
 *
 * **Reacting to changes:**
 * ```kotlin
 * config.onChange { old, new ->
 *     if (old.game.maxDurationMinutes != new.game.maxDurationMinutes) {
 *         restartActiveGames()
 *     }
 * }
 * ```
 *
 * **Automatic file watching:**
 * ```kotlin
 * val config = YamlConfigManager.ref<DuelsConfig>(file)
 *     .withAutoReload()  // starts file watcher
 *     .onChange { old, new -> logger.info("Config reloaded") }
 * ```
 *
 * @param T The configuration data class type.
 * @see YamlConfigManager.ref
 * @since 1.0
 */
class ConfigRef<T : Any> @PublishedApi internal constructor(
    private val file: File,
    private val klass: KClass<T>
) {
    @Volatile
    private var value: T = YamlConfigManager.load(this.file, this.klass)

    private val listeners = CopyOnWriteArrayList<(old: T, new: T) -> Unit>()
    private var watcher: FileWatcher? = null

    /**
     * Returns the current configuration instance.
     *
     * After a [reload] or file-watcher update, this returns the new instance.
     *
     * @since 1.0
     */
    val current: T get() = this.value

    /**
     * Returns the current configuration instance.
     *
     * Shorthand for [current], enabling `config()` syntax.
     *
     * ```kotlin
     * val port = config().server.port
     * ```
     *
     * @return The current configuration instance.
     * @since 1.0
     */
    operator fun invoke(): T = this.value

    /**
     * Enables Kotlin property delegation to the current config.
     *
     * ```kotlin
     * val cfg: DuelsConfig by configRef
     * ```
     *
     * Each property access resolves to the latest config — the delegate
     * is never stale.
     *
     * @since 1.0
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = this.value

    /**
     * Reloads the configuration from disk.
     *
     * Runs the full loading pipeline (read, migrate, deserialize, validate, @Env)
     * and atomically swaps the internal reference. If the new value differs from
     * the old one, all registered [onChange] listeners are notified.
     *
     * ```kotlin
     * fun onReloadCommand() {
     *     val fresh = configRef.reload()
     *     sender.sendMessage("Reloaded! Server ID: ${fresh.serverId}")
     * }
     * ```
     *
     * @return The freshly loaded configuration instance.
     * @since 1.0
     */
    fun reload(): T {
        val old = this.value
        this.value = YamlConfigManager.load(this.file, this.klass)
        this.notifyIfChanged(old, this.value)
        return this.value
    }

    /**
     * Registers a listener that is called whenever the config changes.
     *
     * The listener receives both the old and new config instances, enabling
     * diff-based logic:
     *
     * ```kotlin
     * config.onChange { old, new ->
     *     if (old.database.host != new.database.host) {
     *         reconnectDatabase(new.database)
     *     }
     * }
     * ```
     *
     * Listeners are stored in a [CopyOnWriteArrayList] and are safe to
     * register from any thread. They are invoked on the thread that
     * triggered the reload.
     *
     * @param listener Callback receiving the old and new config instances.
     * @return This [ConfigRef] for chaining.
     * @since 1.0
     */
    fun onChange(listener: (old: T, new: T) -> Unit): ConfigRef<T> {
        this.listeners.add(listener)
        return this
    }

    /**
     * Creates a Kotlin property delegate that extracts a sub-section or
     * derived value from the config.
     *
     * The [selector] is evaluated on every property access, so the
     * returned value is always in sync with the latest config.
     *
     * ```kotlin
     * class StatsManager(config: ConfigRef<DuelsConfig>) {
     *     private val db by config.selecting { it.database }
     *     private val apiKey by config.selecting { it.statsApiKey }
     *     private val dbUrl by config.selecting { "${it.database.host}:${it.database.port}" }
     *
     *     fun connect() {
     *         // db, apiKey, dbUrl — always fresh after any reload
     *     }
     * }
     * ```
     *
     * @param R The type of the selected value.
     * @param selector Function that extracts a value from the config.
     * @return A [ReadOnlyProperty] delegate.
     * @since 1.0
     */
    fun <R> selecting(selector: (T) -> R): ReadOnlyProperty<Any?, R> {
        return ReadOnlyProperty { _, _ -> selector(this.value) }
    }

    /**
     * Starts a file watcher that automatically reloads the config when
     * the file is modified on disk.
     *
     * Changes are debounced — rapid successive writes trigger only one
     * reload. If the new config differs, all [onChange] listeners are notified.
     *
     * ```kotlin
     * val config = YamlConfigManager.ref<DuelsConfig>(file)
     *     .withAutoReload()
     *     .onChange { _, new -> logger.info("Reloaded: ${new.serverId}") }
     * ```
     *
     * @param debounceMs Debounce interval in milliseconds. Defaults to 500.
     * @return This [ConfigRef] for chaining.
     * @see stopAutoReload
     * @since 1.0
     */
    fun withAutoReload(debounceMs: Long = 500): ConfigRef<T> {
        this.stopAutoReload()
        val fw = FileWatcher(this.file, debounceMs) {
            val old = this.value
            this.value = YamlConfigManager.load(this.file, this.klass)
            this.notifyIfChanged(old, this.value)
        }
        this.watcher = fw
        fw.start()
        return this
    }

    /**
     * Stops the automatic file watcher if one is active.
     *
     * If no watcher is running, this method is a no-op.
     *
     * @return This [ConfigRef] for chaining.
     * @see withAutoReload
     * @since 1.0
     */
    fun stopAutoReload(): ConfigRef<T> {
        this.watcher?.stop()
        this.watcher = null
        return this
    }

    /**
     * Saves the current config state back to the file.
     *
     * Useful after programmatic changes or to normalize the YAML format.
     *
     * @since 1.0
     */
    fun save() {
        YamlConfigManager.save(this.file, this.value)
    }

    private fun notifyIfChanged(old: T, new: T) {
        if (old != new) {
            this.listeners.forEach { it(old, new) }
        }
    }
}
