# KConfig

**Type-safe, annotation-driven YAML configuration for Kotlin.**

[![Build](https://img.shields.io/github/actions/workflow/status/maquq/kconfig/build.yml?branch=main&style=flat-square)](https://github.com/maquq/kconfig/actions)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-brightgreen.svg?style=flat-square)](https://github.com/maquq/kconfig/releases)

---

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [ConfigRef (Live Config)](#configref-live-config)
- [Annotations Reference](#annotations-reference)
  - [@Comment](#comment)
  - [@Range](#range)
  - [@Pattern](#pattern)
  - [@Secret](#secret)
  - [@Env](#env)
  - [@MigrateFrom](#migratefrom)
  - [@Transient](#transient)
- [SecretString](#secretstring)
- [Custom Serializers](#custom-serializers)
- [Migrations](#migrations)
- [Error Handling](#error-handling)
- [File Watching](#file-watching)
- [Bukkit Integration](#bukkit-integration)
- [Modules](#modules)
- [License](#license)

---

## Overview

KConfig maps Kotlin data classes to YAML files with zero boilerplate. Define your configuration schema as a data class with default values, annotate fields for validation, comments, secrets, and environment overrides, and let KConfig handle loading, saving, watching, and migration.

**Why KConfig?**

- **Data class driven** -- your schema is a plain Kotlin data class. No DSL, no builder, no YAML parsing code.
- **Automatic file creation** -- missing config files are generated from constructor defaults on first load.
- **Live references** -- `ConfigRef<T>` provides reload-safe access. Every consumer sees the latest config without re-injection.
- **Validation with fallback** -- `@Range` and `@Pattern` enforce constraints. Invalid values fall back to defaults instead of crashing.
- **Secret masking** -- `@Secret` and `SecretString` prevent credential leakage through logs, `toString()`, and debug output.
- **Environment overrides** -- `@Env` binds fields to environment variables with priority: ENV > YAML > default.
- **Schema migrations** -- version-chained `ConfigMigration` with automatic backup before upgrade.
- **File watching** -- WatchService-based auto-reload with debounce.
- **Error reporting** -- all errors collected (no fail-fast), grouped by type, color-coded, with Levenshtein "did you mean?" suggestions.
- **Extensible** -- `TypeSerializer<T>` interface for any custom type. Optional Bukkit integration module.

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("club.skidware:kconfig-core:1.0.0")

    // Optional: Bukkit type serializers
    implementation("club.skidware:kconfig-bukkit:1.0.0")
}
```

### Gradle Version Catalog

```toml
# gradle/libs.versions.toml

[versions]
kconfig = "1.0.0"

[libraries]
kconfig-core = { module = "club.skidware:kconfig-core", version.ref = "kconfig" }
kconfig-bukkit = { module = "club.skidware:kconfig-bukkit", version.ref = "kconfig" }
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.kconfig.core)
    implementation(libs.kconfig.bukkit) // optional
}
```

### Maven

```xml
<dependency>
    <groupId>club.skidware</groupId>
    <artifactId>kconfig-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Optional: Bukkit type serializers -->
<dependency>
    <groupId>club.skidware</groupId>
    <artifactId>kconfig-bukkit</artifactId>
    <version>1.0.0</version>
</dependency>
```

`kconfig-core` has no Bukkit dependency and works in any Kotlin/JVM project (JDK 21+).

---

## Quick Start

Define a data class with default values, then load it from a file:

```kotlin
import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.annotation.Comment
import java.io.File

data class ServerConfig(
    @Comment("The unique identifier for this server instance")
    val serverId: String = "lobby-01",

    @Comment("Maximum concurrent players")
    val maxPlayers: Int = 200,

    @Comment("Enable debug logging")
    val debug: Boolean = false
)

fun main() {
    // Load from file -- creates it with defaults if it does not exist
    val config = YamlConfigManager.load<ServerConfig>(File("config.yml"))

    println(config.serverId)   // "lobby-01"
    println(config.maxPlayers) // 200

    // Save changes back
    val updated = config.copy(maxPlayers = 500)
    YamlConfigManager.save(File("config.yml"), updated)
}
```

The generated YAML:

```yaml
# The unique identifier for this server instance
serverId: lobby-01

# Maximum concurrent players
maxPlayers: 200

# Enable debug logging
debug: false
```

---

## ConfigRef (Live Config)

### The Problem

When a configuration is reloaded from disk, every class that captured the old instance holds a stale reference. You end up threading reload logic through your entire application, or resorting to mutable global state.

### The Solution

`ConfigRef<T>` is a reload-safe wrapper around a configuration instance. Any class holding a `ConfigRef` automatically sees the latest values after a reload -- no re-injection, no manual wiring.

### Creating a ConfigRef

```kotlin
import club.skidware.kconfig.ConfigRef
import club.skidware.kconfig.YamlConfigManager
import java.io.File

val config: ConfigRef<ServerConfig> = YamlConfigManager.ref<ServerConfig>(File("config.yml"))
```

This loads the config from disk immediately and returns a live reference. Pass this `ConfigRef` to any class that needs config access.

### Access Patterns

`ConfigRef` provides three ways to read the current config. All three resolve to the latest instance after any reload.

```kotlin
class GameManager(private val config: ConfigRef<ServerConfig>) {

    // 1. Invoke operator -- concise, function-call style
    fun getServerId(): String = config().serverId

    // 2. Property access -- explicit and readable
    fun getMaxPlayers(): Int = config.current.maxPlayers

    // 3. Kotlin delegate -- zero-boilerplate field access
    private val cfg: ServerConfig by config
    fun isDebug(): Boolean = cfg.debug
}
```

### Selecting Sub-Sections

Use `selecting {}` to create delegates that extract a specific section or derived value from the config. The selector runs on every access, so the result is always fresh.

```kotlin
data class AppConfig(
    val database: DatabaseConfig = DatabaseConfig(),
    val messages: MessagesConfig = MessagesConfig(),
    val limits: LimitsConfig = LimitsConfig()
)

data class DatabaseConfig(
    val host: String = "localhost",
    val port: Int = 5432
)

data class MessagesConfig(
    val prefix: String = "[Server] ",
    val welcome: String = "Welcome!"
)

data class LimitsConfig(
    val maxConnections: Int = 100
)

class ConnectionManager(config: ConfigRef<AppConfig>) {
    private val db by config.selecting { it.database }
    private val limits by config.selecting { it.limits }
    private val connectionString by config.selecting { "${it.database.host}:${it.database.port}" }

    fun connect() {
        // db, limits, connectionString -- always in sync with the latest config
        println("Connecting to $connectionString (max: ${limits.maxConnections})")
    }
}
```

### Change Callbacks

Register listeners that fire when the config changes. Listeners receive both the old and new instances, enabling diff-based logic.

```kotlin
config.onChange { old, new ->
    if (old.database.host != new.database.host) {
        reconnectDatabase(new.database)
    }
}
```

Listeners are stored in a `CopyOnWriteArrayList` and are safe to register from any thread. They execute on the thread that triggered the reload.

### Automatic File Watching

Chain `withAutoReload()` to start a file watcher that reloads the config whenever the file changes on disk. Changes are debounced (default 500ms).

```kotlin
val config = YamlConfigManager.ref<ServerConfig>(File("config.yml"))
    .withAutoReload()
    .onChange { old, new -> println("Config reloaded: ${new.serverId}") }
```

To customize the debounce interval:

```kotlin
val config = YamlConfigManager.ref<ServerConfig>(File("config.yml"))
    .withAutoReload(debounceMs = 1000)
```

Stop the watcher when you no longer need it:

```kotlin
config.stopAutoReload()
```

### Manual Reload

Trigger a reload programmatically (e.g., from a command):

```kotlin
fun onReloadCommand() {
    val fresh = config.reload()
    println("Reloaded. Server ID: ${fresh.serverId}")
}
```

### Saving

Write the current in-memory state back to disk:

```kotlin
config.save()
```

### Full Example: Plugin with Multiple Managers

```kotlin
import club.skidware.kconfig.ConfigRef
import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.annotation.Comment
import club.skidware.kconfig.annotation.Range
import club.skidware.kconfig.annotation.Secret
import club.skidware.kconfig.serializer.SecretString
import java.io.File

// -- Configuration schema --

data class PluginConfig(
    @Comment("Server identity")
    val server: ServerSection = ServerSection(),

    @Comment("Game settings")
    val game: GameSection = GameSection(),

    @Comment("Database connection")
    val database: DatabaseSection = DatabaseSection()
)

data class ServerSection(
    val id: String = "lobby-01",

    @Range(min = 1.0, max = 65535.0)
    val port: Int = 25565
)

data class GameSection(
    @Range(min = 1.0, max = 60.0)
    val maxDurationMinutes: Int = 15,

    val defaultMode: String = "solo"
)

data class DatabaseSection(
    val host: String = "localhost",
    val port: Int = 5432,

    @Secret
    val password: SecretString = SecretString("")
)

// -- Managers that consume ConfigRef --

class GameManager(config: ConfigRef<PluginConfig>) {
    private val game by config.selecting { it.game }

    fun startGame() {
        println("Starting ${game.defaultMode} game (max ${game.maxDurationMinutes} min)")
    }
}

class DatabaseManager(config: ConfigRef<PluginConfig>) {
    private val db by config.selecting { it.database }

    fun connect() {
        println("Connecting to ${db.host}:${db.port}")
    }
}

// -- Initialization --

fun main() {
    val config = YamlConfigManager.ref<PluginConfig>(File("config.yml"))
        .withAutoReload()
        .onChange { old, new ->
            if (old.game.maxDurationMinutes != new.game.maxDurationMinutes) {
                println("Game duration changed, restarting active games")
            }
        }

    val gameManager = GameManager(config)
    val dbManager = DatabaseManager(config)

    gameManager.startGame()
    dbManager.connect()

    // On shutdown:
    config.stopAutoReload()
}
```

---

## Annotations Reference

| Annotation | Target | Purpose |
|---|---|---|
| `@Comment` | Any field | Adds YAML comments (above or inline) |
| `@Range` | Numeric fields | Enforces inclusive `[min, max]` bounds |
| `@Pattern` | String fields | Validates against a regular expression |
| `@Secret` | Any field | Masks value in debug output and error reports |
| `@Env` | Any field | Overrides value from an environment variable |
| `@MigrateFrom` | Any field | Maps legacy YAML keys to the current field |
| `@Transient` | Any field | Excludes field from YAML serialization |

---

### @Comment

Adds human-readable comments to the generated YAML. Supports multiple lines and two placement modes.

```kotlin
import club.skidware.kconfig.annotation.Comment
import club.skidware.kconfig.annotation.CommentPlacement

data class AppConfig(
    @Comment("The application display name")
    val name: String = "my-app",

    @Comment("Port to bind to", placement = CommentPlacement.INLINE)
    val port: Int = 8080,

    @Comment("Database connection settings", "Restart required after changes")
    val database: DatabaseConfig = DatabaseConfig()
)
```

Generated YAML with `ABOVE` placement (default):

```yaml
# The application display name
name: my-app
```

Generated YAML with `INLINE` placement:

```yaml
port: 8080 # Port to bind to
```

Multi-line comments render as consecutive comment lines:

```yaml
# Database connection settings
# Restart required after changes
database:
  host: localhost
```

---

### @Range

Constrains numeric fields to an inclusive `[min, max]` range. Works with `Int`, `Long`, `Float`, and `Double`. Out-of-range values fall back to the data class default and produce an `OutOfRange` error in the report.

```kotlin
import club.skidware.kconfig.annotation.Range

data class GameConfig(
    @Range(min = 1.0, max = 1000.0)
    val maxPlayers: Int = 200,

    @Range(min = 0.0, max = 1.0)
    val loadFactor: Double = 0.75,

    @Range(min = 1.0, max = 65535.0)
    val port: Int = 8080
)
```

If `config.yml` contains `maxPlayers: 5000`, KConfig loads the default value `200` and prints:

```
[OutOfRange]
  maxPlayers: Value 5000 is out of range [1.0, 1000.0], fell back to 200
```

---

### @Pattern

Validates string fields against a regular expression. Non-matching values fall back to the default. An optional `description` parameter provides a human-readable explanation in error messages.

```kotlin
import club.skidware.kconfig.annotation.Pattern

data class NetworkConfig(
    @Pattern(
        regex = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$",
        description = "Must be a valid IPv4 address"
    )
    val bindAddress: String = "0.0.0.0",

    @Pattern(regex = "^[a-z][a-z0-9-]*$")
    val hostname: String = "app-server"
)
```

---

### @Secret

Marks fields as sensitive. Values are masked in debug output (`toDebugString`), error reports, and reload logs. The actual value is always written to YAML in plaintext -- masking is a display-layer concern.

Three masking strategies are available:

| Strategy | Input | Output | Use Case |
|---|---|---|---|
| `FULL` (default) | `superSecret123` | `********` | Passwords, tokens |
| `PARTIAL` | `superSecret123` | `supe********` | Identifying which key is in use |
| `EDGES` | `superSecret123` | `s*************3` | Quick visual verification |

```kotlin
import club.skidware.kconfig.annotation.Secret
import club.skidware.kconfig.annotation.MaskStrategy

data class CredentialsConfig(
    @Secret
    val password: String = "",

    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 4)
    val databaseUrl: String = "jdbc:postgresql://localhost/mydb",

    @Secret(mask = MaskStrategy.EDGES)
    val apiKey: String = ""
)
```

Use `toDebugString` to produce safe diagnostic output:

```kotlin
val debug = YamlConfigManager.toDebugString(config)
println(debug)
// password: "********"
// databaseUrl: "jdbc********"
// apiKey: "s*************3"
```

---

### @Env

Binds a field to an environment variable. When the variable is set, its value takes precedence over both the YAML file value and the data class default.

**Priority order:** Environment variable > YAML file > data class default.

Environment values are never written back to the YAML file.

```kotlin
import club.skidware.kconfig.annotation.Env
import club.skidware.kconfig.annotation.Secret

data class DatabaseConfig(
    @Env("DB_HOST")
    val host: String = "localhost",

    @Env("DB_PORT")
    val port: Int = 5432,

    @Env("DB_PASSWORD") @Secret
    val password: String = ""
)
```

```bash
# At runtime:
export DB_HOST=prod-db.internal
export DB_PASSWORD=s3cr3t

# config.yml says host: localhost, but the loaded instance will have host = "prod-db.internal"
```

---

### @MigrateFrom

Provides backward compatibility when renaming fields. During deserialization, if the current key is absent, the old keys are checked in the order provided.

```kotlin
import club.skidware.kconfig.annotation.MigrateFrom

data class ServerConfig(
    @MigrateFrom("server-name", "serverName")
    val name: String = "default",

    @MigrateFrom("max-players")
    val maxPlayers: Int = 100
)
```

With this config on disk:

```yaml
server-name: production
max-players: 500
```

KConfig loads `name = "production"` and `maxPlayers = 500`, then re-saves the file with the current key names:

```yaml
name: production
maxPlayers: 500
```

---

### @Transient

Excludes a field from YAML serialization and deserialization. The field always uses its data class default value. Use this for computed properties, internal timestamps, or runtime-only state.

```kotlin
import club.skidware.kconfig.annotation.Transient

data class AppConfig(
    val name: String = "my-app",

    @Transient
    val startedAt: Long = System.currentTimeMillis(),

    @Transient
    val isDebugBuild: Boolean = false
)
```

The generated YAML will contain only `name` -- `startedAt` and `isDebugBuild` are omitted entirely.

---

## SecretString

`SecretString` is an inline value class that wraps a `String` and guarantees that `toString()` never exposes the plaintext. Use it for fields that must never leak through logging, string interpolation, or exception messages.

```kotlin
import club.skidware.kconfig.serializer.SecretString
import club.skidware.kconfig.YamlConfigManager
import java.io.File

data class ApiConfig(
    val endpoint: String = "https://api.example.com",
    val apiKey: SecretString = SecretString("")
)

fun main() {
    val config = YamlConfigManager.load<ApiConfig>(File("api.yml"))

    println(config.apiKey)            // prints: ********
    println("Key=${config.apiKey}")   // prints: Key=********
    println(config.apiKey.expose())   // prints the actual value (intentional access)
}
```

Because `SecretString` is an inline `value class`, it has zero runtime allocation overhead compared to a raw `String`.

Fields of type `SecretString` are automatically detected and masked with `FULL` strategy even without a `@Secret` annotation.

---

## Custom Serializers

Implement the `TypeSerializer<T>` interface to add support for any type. A serializer must round-trip: calling `deserialize` on the output of `serialize` must produce an equivalent value.

```kotlin
import club.skidware.kconfig.serializer.TypeSerializer
import club.skidware.kconfig.YamlConfigManager
import java.time.Instant

object InstantSerializer : TypeSerializer<Instant> {
    override fun serialize(value: Instant): Any = value.toString()
    override fun deserialize(raw: Any): Instant = Instant.parse(raw.toString())
}
```

Register the serializer before loading any configs that use the type:

```kotlin
YamlConfigManager.registerSerializer(Instant::class, InstantSerializer)
```

Or use the reified overload:

```kotlin
YamlConfigManager.registerSerializer<Instant>(InstantSerializer)
```

**Built-in serializers** cover: `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, enums, `List`, `Map`, nested data classes, and `SecretString`.

---

## Migrations

Use `ConfigMigration` for version-chained schema upgrades. Each migration handles exactly one version increment. The `MigrationRunner` chains them automatically (v1 -> v2 -> v3) and creates a backup before migrating.

### Defining Migrations

```kotlin
import club.skidware.kconfig.migration.ConfigMigration

data class MyConfig(
    val configVersion: Int = 3,
    val name: String = "default",
    val maxConnections: Int = 100,
    val timeout: Int = 30
)

object MigrateV1ToV2 : ConfigMigration {
    override val fromVersion = 1
    override val toVersion = 2

    override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
        map["name"] = map.remove("serverName")
        map.putIfAbsent("maxConnections", 100)
        return map
    }
}

object MigrateV2ToV3 : ConfigMigration {
    override val fromVersion = 2
    override val toVersion = 3

    override fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?> {
        map.putIfAbsent("timeout", 30)
        return map
    }
}
```

### Registering and Running

```kotlin
YamlConfigManager.registerMigration(MyConfig::class, MigrateV1ToV2)
YamlConfigManager.registerMigration(MyConfig::class, MigrateV2ToV3)

val config = YamlConfigManager.load<MyConfig>(File("config.yml"))
```

### Backup Behavior

Before any migration runs, a backup is created as `<filename>.v<version>.bak`. For example, migrating `config.yml` from version 1 produces `config.yml.v1.bak`. Backups are only created once per version -- subsequent loads do not overwrite existing backups.

### Migration Chain

The target version is read from the `configVersion` field of the data class default. If the file's `configVersion` is lower than the target, the runner applies each migration step in sequence. If a gap exists in the chain (no migration for a required step), an `IllegalStateException` is thrown.

---

## Error Handling

KConfig collects all validation errors instead of failing on the first one. After loading, errors are grouped by type and printed with ANSI color-coded formatting to stderr.

### Error Types

| Type | Cause | Behavior |
|---|---|---|
| `InvalidValue` | Wrong type for a field (e.g., string where `Int` expected) | Falls back to default |
| `UnknownType` | No serializer registered for a type | Falls back to default |
| `UnknownKey` | Unrecognized key in YAML | Reported with Levenshtein "did you mean?" suggestion |
| `OutOfRange` | Numeric value outside `@Range` bounds | Falls back to default |
| `PatternMismatch` | String does not match `@Pattern` regex | Falls back to default |
| `MissingRequired` | Required field (no default, non-nullable) is absent | Cannot fall back; error reported |

### Fallback Behavior

When a field fails validation, KConfig falls back to the data class default value and continues loading the rest of the configuration. The error report lists every issue found.

### Example Error Output

```
3 config error(s) found in config.yml:

  [InvalidValue]
    server.port: Invalid value 'abc', expected Int

  [UnknownKey]
    databse: Unknown key 'databse' - did you mean 'database'?

  [OutOfRange]
    server.maxThreads: Value 9999 is out of range [1.0, 512.0], fell back to 16

  1 field(s) fell back to default values.
```

### Configuring Output

By default, errors are printed to `System.err`. Redirect to a different stream:

```kotlin
YamlConfigManager.setOutput(System.out)
```

---

## File Watching

Monitor a config file for changes and auto-reload with debounce. KConfig uses the Java NIO `WatchService` to detect file modifications. Rapid successive writes are collapsed into a single reload.

### Basic Usage

```kotlin
val watcher = YamlConfigManager.watch<ServerConfig>(File("config.yml")) { newConfig ->
    println("Config reloaded: serverId=${newConfig.serverId}")
}
```

### Stopping Watchers

```kotlin
// Stop a single watcher
watcher.stop()

// Stop all active watchers (call on shutdown)
YamlConfigManager.stopAllWatchers()
```

### Debounce

The default debounce interval is 500ms. When a file modification is detected, the reload callback is scheduled after the debounce period. If additional modifications occur within that window, they are collapsed into a single reload.

### Error Safety

If the reloaded file contains validation errors, they are reported and the previous valid configuration is preserved. Exceptions during reload are caught and printed to the configured output stream.

### ConfigRef vs. watch

For most use cases, prefer `ConfigRef.withAutoReload()` over the lower-level `watch` API. `ConfigRef` provides reload-safe references, change callbacks, and sub-section delegates in addition to file watching.

---

## Bukkit Integration

The `kconfig-bukkit` module provides serializers for common Bukkit types. Register them once during plugin initialization.

### Setup

```kotlin
import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.bukkit.BukkitSerializers

override fun onEnable() {
    BukkitSerializers.registerAll(YamlConfigManager)
}
```

### Supported Types

| Type | YAML Format | Notes |
|---|---|---|
| `Location` | `{world, x, y, z, pitch?, yaw?}` | `pitch` and `yaw` omitted when zero |
| `Vector` | `{x, y, z}` | Double-precision coordinates |
| `Color` | `{red, green, blue}` | Integer values 0-255 |
| `ItemStack` | Bukkit standard serialization | Delegates to `ItemStack.serialize()` |

### YAML Examples

**Location:**

```yaml
spawn:
  world: world
  x: 100.5
  y: 64.0
  z: -200.3
  pitch: 1.5
  yaw: 90.0
```

**Vector:**

```yaml
velocity:
  x: 1.0
  y: 0.5
  z: -1.0
```

**Color:**

```yaml
particleColor:
  red: 255
  green: 128
  blue: 0
```

### Full Example

```kotlin
import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.ConfigRef
import club.skidware.kconfig.bukkit.BukkitSerializers
import club.skidware.kconfig.annotation.Comment
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Color
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

data class ArenaConfig(
    @Comment("Lobby spawn point")
    val lobbySpawn: Location = Location(Bukkit.getWorld("world"), 0.0, 64.0, 0.0),

    @Comment("Particle effect color")
    val particleColor: Color = Color.fromRGB(255, 0, 0)
)

class ArenaPlugin : JavaPlugin() {
    private lateinit var config: ConfigRef<ArenaConfig>

    override fun onEnable() {
        BukkitSerializers.registerAll(YamlConfigManager)
        config = YamlConfigManager.ref<ArenaConfig>(dataFolder.resolve("arena.yml"))
            .withAutoReload()
    }

    override fun onDisable() {
        config.stopAutoReload()
    }
}
```

---

## Modules

KConfig is organized into three modules:

| Module | Artifact | Description |
|---|---|---|
| `kconfig-core` | `club.skidware:kconfig-core` | Core library. YAML loading, saving, validation, annotations, migrations, file watching, `ConfigRef`. No external dependencies beyond SnakeYAML, kotlin-reflect, and SLF4J. |
| `kconfig-bukkit` | `club.skidware:kconfig-bukkit` | Optional Bukkit integration. Serializers for `Location`, `Vector`, `Color`, and `ItemStack`. Depends on `kconfig-core` and the Bukkit API. |
| `kconfig-example` | -- | Example project demonstrating configuration schemas, migrations, and custom serializers. Not published. |

---

## License

KConfig is licensed under the [MIT License](LICENSE).
