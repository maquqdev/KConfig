package club.skidware.kconfig.watcher

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Watches a single file for modifications and triggers a debounced reload callback.
 *
 * Uses the Java NIO [WatchService][java.nio.file.WatchService] to monitor the
 * parent directory for `ENTRY_MODIFY` events. Only events matching the target
 * [file] name are processed; all others are ignored.
 *
 * **Debounce behavior:** When a file modification is detected, the [onReload]
 * callback is scheduled to run after [debounceMs] milliseconds. Rapid successive
 * modifications within the debounce window are collapsed into a single reload,
 * preventing redundant reloads during multi-write save operations.
 *
 * **Threading:** The watcher runs on a single daemon thread named
 * `kconfig-watcher-<filename>`. Because the thread is a daemon, it does not
 * prevent JVM shutdown. The thread polls for events every 1 second.
 *
 * **Lifecycle:**
 * 1. Create a `FileWatcher` instance.
 * 2. Call [start] to begin monitoring. Calling `start` multiple times is safe (no-op).
 * 3. Call [stop] to terminate the watcher, shut down the thread pool, and close
 *    the watch service. The watcher cannot be restarted after stopping.
 *
 * Example:
 * ```kotlin
 * val watcher = FileWatcher(File("config.yml"), debounceMs = 1000) {
 *     println("Config file changed, reloading...")
 *     // reload logic here
 * }
 * watcher.start()
 *
 * //Later, to stop watching:
 * watcher.stop()
 * ```
 *
 * @param file The file to watch for modifications.
 * @param debounceMs The debounce delay in milliseconds. Defaults to `500`.
 * @param onReload The callback invoked when the file is modified (after debounce).
 * @see club.skidware.kconfig.YamlConfigManager.watch
 * @see club.skidware.kconfig.YamlConfigManager.stopWatching
 * @since 1.0
 */
class FileWatcher(
    private val file: File,
    private val debounceMs: Long = 500,
    private val onReload: () -> Unit
) {
    private val logger = LoggerFactory.getLogger(FileWatcher::class.java)
    private val watchService = FileSystems.getDefault().newWatchService()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "kconfig-watcher-${file.name}").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private val lastModified = AtomicLong(0)

    /**
     * Starts watching the file for modifications.
     *
     * Registers the parent directory with the [WatchService][java.nio.file.WatchService]
     * and begins polling on a daemon thread. If the watcher is already running,
     * this method is a no-op.
     *
     * @since 1.0
     */
    fun start() {
        if (this.running.getAndSet(true)) return

        val dir = this.file.absoluteFile.parentFile?.toPath() ?: return
        dir.register(this.watchService, StandardWatchEventKinds.ENTRY_MODIFY)

        this.scheduler.submit {
            while (this.running.get()) {
                try {
                    val key = this.watchService.poll(1, TimeUnit.SECONDS) ?: continue
                    var relevant = false

                    for (event in key.pollEvents()) {
                        val context = event.context()
                        if (context is Path && context.fileName.toString() == this.file.name) {
                            relevant = true
                        }
                    }
                    key.reset()

                    if (relevant) {
                        val now = System.currentTimeMillis()
                        val last = this.lastModified.get()
                        if (now - last >= this.debounceMs) {
                            this.lastModified.set(now)
                            // Debounce: schedule reload after debounceMs
                            this.scheduler.schedule({
                                try {
                                    this.onReload()
                                } catch (e: Exception) {
                                    this.logger.error("Error during file watch reload: {}", e.message, e)
                                }
                            }, this.debounceMs, TimeUnit.MILLISECONDS)
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    if (this.running.get()) {
                        this.logger.error("FileWatcher error: {}", e.message, e)
                    }
                }
            }
        }
    }

    /**
     * Stops watching the file and releases all resources.
     *
     * Shuts down the scheduler thread pool and closes the watch service.
     * After calling this method, the watcher cannot be restarted.
     *
     * @since 1.0
     */
    fun stop() {
        this.running.set(false)
        this.scheduler.shutdownNow()
        this.watchService.close()
    }
}
