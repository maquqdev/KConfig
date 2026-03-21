<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1ABC9C,50:2980B9,100:6C3483&height=220&section=header&text=KConfig&fontSize=75&fontColor=ffffff&fontAlignY=35&desc=Type-safe%2C%20annotation-driven%20YAML%20configuration%20for%20Kotlin&descSize=18&descAlignY=55&animation=fadeIn" width="100%"/>

<br/>

[![Build](https://img.shields.io/github/actions/workflow/status/maquqdev/KConfig/ci.yml?branch=main&style=flat&label=CI)](https://github.com/maquqdev/KConfig/actions)
[![JitPack](https://jitpack.io/v/maquqdev/KConfig.svg)](https://jitpack.io/#maquqdev/KConfig)
[![License](https://img.shields.io/badge/license-MIT-6C3483?style=flat)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-%237F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![JDK](https://img.shields.io/badge/JDK-21+-%23ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![KDoc](https://img.shields.io/badge/docs-KDoc-%232980B9)](https://maquqdev.github.io/KConfig/)

**Map Kotlin data classes to YAML files with zero boilerplate. Validate, reload, migrate — automatically.**

[📖 Wiki](https://github.com/maquqdev/KConfig/wiki) · [🐛 Issues](https://github.com/maquqdev/KConfig/issues) · [📦 JitPack](https://jitpack.io/#maquqdev/KConfig)

</div>

---

## ⚡ Why KConfig?

<table>
<tr>
<td width="50%">

### 🚫 Manual YAML Handling
```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        saveDefaultConfig()
        val id = config.getString("serverId") ?: "default"
        val max = config.getInt("maxPlayers", 200)
        if (max < 1 || max > 1000) {
            logger.warning("Invalid maxPlayers!")
            // fallback? crash? ignore?
        }
        // No type safety, no validation,
        // no reload, no comments preserved,
        // stale references everywhere...
    }
}
```

</td>
<td width="50%">

### ✅ KConfig
```kotlin
data class ServerConfig(
    @Comment("Server instance ID")
    val serverId: String = "lobby-01",

    @Comment("Max concurrent players")
    @Range(min = 1.0, max = 1000.0)
    val maxPlayers: Int = 200
)

fun main() {
    val config = YamlConfigManager
        .load<ServerConfig>(File("config.yml"))
    // Type-safe, validated, comments preserved,
    // auto-created from defaults. Done.
}
```

> 🎯 Schema = data class. Validation, comments, reload — **all automatic.**

</td>
</tr>
</table>

---

## 🧩 Features

<table>
<tr>
<td>

### 🏗️ Core
![DataClass](https://img.shields.io/badge/data_class-driven_schema-1ABC9C?style=flat-square)
![AutoCreate](https://img.shields.io/badge/auto-file_creation-2980B9?style=flat-square)
![Comments](https://img.shields.io/badge/@Comment-preserved_in_YAML-27ae60?style=flat-square)
![Validation](https://img.shields.io/badge/@Range_@Pattern-validation-6C3483?style=flat-square)

</td>
<td>

### 🔒 Safety
![ErrorReport](https://img.shields.io/badge/errors-collected_not_crashed-e74c3c?style=flat-square)
![Secret](https://img.shields.io/badge/@Secret-credential_masking-e67e22?style=flat-square)
![Fallback](https://img.shields.io/badge/invalid_values-fallback_to_defaults-f1c40f?style=flat-square)
![DidYouMean](https://img.shields.io/badge/unknown_keys-did_you_mean%3F-e74c3c?style=flat-square)

</td>
</tr>
<tr>
<td>

### 🔄 Live Config
![ConfigRef](https://img.shields.io/badge/ConfigRef-reload_safe_access-7F52FF?style=flat-square)
![FileWatch](https://img.shields.io/badge/WatchService-auto_reload-8e44ad?style=flat-square)
![Callbacks](https://img.shields.io/badge/onChange-diff_based_listeners-9b59b6?style=flat-square)
![Selecting](https://img.shields.io/badge/selecting-sub_section_delegates-6C3483?style=flat-square)

</td>
<td>

### 🔌 Extensibility
![Env](https://img.shields.io/badge/@Env-environment_overrides-16a085?style=flat-square)
![Migration](https://img.shields.io/badge/migrations-version_chained-1abc9c?style=flat-square)
![Serializers](https://img.shields.io/badge/TypeSerializer-custom_types-2ecc71?style=flat-square)
![Bukkit](https://img.shields.io/badge/kconfig--bukkit-Location_Vector_Color-27ae60?style=flat-square)

</td>
</tr>
</table>

---

## 🚀 Quick Start

### 1️⃣ Add the dependency

```kotlin
// build.gradle.kts
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.maquqdev.KConfig:kconfig-core:v1.0.0")

    // Optional: Bukkit type serializers
    implementation("com.github.maquqdev.KConfig:kconfig-bukkit:v1.0.0")
}
```

<details>
<summary>📋 Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.maquqdev.KConfig</groupId>
    <artifactId>kconfig-core</artifactId>
    <version>v1.0.0</version>
</dependency>

<!-- Optional: Bukkit type serializers -->
<dependency>
    <groupId>com.github.maquqdev.KConfig</groupId>
    <artifactId>kconfig-bukkit</artifactId>
    <version>v1.0.0</version>
</dependency>
```

</details>

<details>
<summary>📋 Gradle Version Catalog</summary>

```toml
# gradle/libs.versions.toml
[versions]
kconfig = "v1.0.0"

[libraries]
kconfig-core = { module = "com.github.maquqdev.KConfig:kconfig-core", version.ref = "kconfig" }
kconfig-bukkit = { module = "com.github.maquqdev.KConfig:kconfig-bukkit", version.ref = "kconfig" }
```

```kotlin
dependencies {
    implementation(libs.kconfig.core)
    implementation(libs.kconfig.bukkit) // optional
}
```

</details>

### 2️⃣ Define your schema

```kotlin
data class ServerConfig(
    @Comment("The unique identifier for this server instance")
    val serverId: String = "lobby-01",

    @Comment("Maximum concurrent players")
    @Range(min = 1.0, max = 1000.0)
    val maxPlayers: Int = 200,

    @Comment("Enable debug logging")
    val debug: Boolean = false
)
```

### 3️⃣ Load and use

```kotlin
val config = YamlConfigManager.load<ServerConfig>(File("config.yml"))

println(config.serverId)   // "lobby-01"
println(config.maxPlayers) // 200
```

> If the file doesn't exist, it's auto-created from defaults — with comments preserved:

```yaml
# The unique identifier for this server instance
serverId: lobby-01

# Maximum concurrent players
maxPlayers: 200

# Enable debug logging
debug: false
```

---

## 📚 Key Concepts

<details>
<summary><b>🔄 ConfigRef — Reload-Safe Live Config</b></summary>

Every class holding a `ConfigRef` automatically sees the latest values after a reload — no re-injection, no manual wiring.

```kotlin
val config: ConfigRef<ServerConfig> = YamlConfigManager.ref<ServerConfig>(File("config.yml"))

class GameManager(private val config: ConfigRef<ServerConfig>) {

    // Three access patterns — all resolve to the latest instance
    fun example() {
        config().serverId              // invoke operator
        config.current.maxPlayers      // property access
    }

    // Kotlin delegate
    private val cfg: ServerConfig by config
}
```

**Sub-section delegates** — extract specific sections that stay in sync:

```kotlin
class ConnectionManager(config: ConfigRef<AppConfig>) {
    private val db by config.selecting { it.database }
    private val connStr by config.selecting { "${it.database.host}:${it.database.port}" }
}
```

**Change callbacks** with diff-based logic:

```kotlin
config.onChange { old, new ->
    if (old.database.host != new.database.host) {
        reconnectDatabase(new.database)
    }
}
```

**Auto-reload** from disk with debounce:

```kotlin
val config = YamlConfigManager.ref<ServerConfig>(File("config.yml"))
    .withAutoReload(debounceMs = 500)
    .onChange { old, new -> println("Reloaded!") }

// On shutdown:
config.stopAutoReload()
```

</details>

<details>
<summary><b>🏷️ Annotations Reference</b></summary>

| Annotation | Target | Purpose |
|:-----------|:-------|:--------|
| `@Comment` | Any field | Adds YAML comments (above or inline) |
| `@Range` | Numeric fields | Enforces inclusive `[min, max]` bounds |
| `@Pattern` | String fields | Validates against a regex |
| `@Secret` | Any field | Masks value in debug output and error reports |
| `@Env` | Any field | Overrides value from an environment variable |
| `@MigrateFrom` | Any field | Maps legacy YAML keys to the current field |
| `@Transient` | Any field | Excludes field from YAML serialization |

```kotlin
data class AppConfig(
    @Comment("Display name")
    val name: String = "my-app",

    @Comment("Port to bind to", placement = CommentPlacement.INLINE)
    @Range(min = 1.0, max = 65535.0)
    val port: Int = 8080,

    @Pattern(regex = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$", description = "IPv4 address")
    val bindAddress: String = "0.0.0.0",

    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 4)
    val databaseUrl: String = "jdbc:postgresql://localhost/mydb",

    @Env("APP_TOKEN") @Secret
    val token: SecretString = SecretString(""),

    @MigrateFrom("server-name", "serverName")
    val serverName: String = "default",

    @Transient
    val startedAt: Long = System.currentTimeMillis()
)
```

</details>

<details>
<summary><b>🔐 Secrets & Environment Overrides</b></summary>

### @Secret — Credential Masking

Three masking strategies prevent leaks through logs, `toString()`, and debug output:

| Strategy | Input | Output | Use Case |
|:---------|:------|:-------|:---------|
| `FULL` (default) | `superSecret123` | `********` | Passwords, tokens |
| `PARTIAL` | `superSecret123` | `supe********` | Identifying which key |
| `EDGES` | `superSecret123` | `s*************3` | Quick visual check |

### SecretString — Zero-Allocation Safety

```kotlin
data class ApiConfig(
    val apiKey: SecretString = SecretString("")
)

println(config.apiKey)           // ********
println(config.apiKey.expose())  // actual value (intentional access)
```

`SecretString` is an inline `value class` — zero runtime overhead.

### @Env — Environment Variable Overrides

Priority: **ENV > YAML > default**. Env values are never written back to YAML.

```kotlin
data class DatabaseConfig(
    @Env("DB_HOST") val host: String = "localhost",
    @Env("DB_PASSWORD") @Secret val password: String = ""
)
```

```bash
export DB_HOST=prod-db.internal  # overrides YAML and default
```

</details>

<details>
<summary><b>📦 Migrations</b></summary>

Version-chained schema upgrades with automatic backup before each migration.

```kotlin
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

// Register and load — chain runs automatically (v1 → v2 → v3)
YamlConfigManager.registerMigration(MyConfig::class, MigrateV1ToV2)
YamlConfigManager.registerMigration(MyConfig::class, MigrateV2ToV3)
val config = YamlConfigManager.load<MyConfig>(File("config.yml"))
```

Backups are created as `config.yml.v1.bak` before migration. Gaps in the chain throw `IllegalStateException`.

</details>

<details>
<summary><b>🔧 Custom Serializers</b></summary>

Implement `TypeSerializer<T>` for any type:

```kotlin
object InstantSerializer : TypeSerializer<Instant> {
    override fun serialize(value: Instant): Any = value.toString()
    override fun deserialize(raw: Any): Instant = Instant.parse(raw.toString())
}

YamlConfigManager.registerSerializer<Instant>(InstantSerializer)
```

**Built-in serializers:** `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, enums, `List`, `Map`, nested data classes, `SecretString`.

</details>

<details>
<summary><b>🎮 Bukkit Integration</b></summary>

The optional `kconfig-bukkit` module adds serializers for common Bukkit types.

```kotlin
override fun onEnable() {
    BukkitSerializers.registerAll(YamlConfigManager)
}
```

| Type | YAML Format | Notes |
|:-----|:------------|:------|
| `Location` | `{world, x, y, z, pitch?, yaw?}` | pitch/yaw omitted when zero |
| `Vector` | `{x, y, z}` | Double-precision |
| `Color` | `{red, green, blue}` | 0-255 |
| `ItemStack` | `{displayName, lore, custom-model-data...}` | Full serialization |

```kotlin
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

</details>

---

## 🛡️ Error Handling

KConfig collects **all** validation errors instead of failing on the first one. Invalid values fall back to defaults — your app never crashes from bad config.

```
3 config error(s) found in config.yml:

  [InvalidValue]
    server.port: Invalid value 'abc', expected Int

  [UnknownKey]
    databse: Unknown key 'databse' — did you mean 'database'?

  [OutOfRange]
    server.maxThreads: Value 9999 is out of range [1.0, 512.0], fell back to 16

  1 field(s) fell back to default values.
```

<details>
<summary>📋 All error types</summary>

| Type | Cause | Behavior |
|:-----|:------|:---------|
| `InvalidValue` | Wrong type (e.g., string where `Int` expected) | Falls back to default |
| `UnknownType` | No serializer registered | Falls back to default |
| `UnknownKey` | Unrecognized key in YAML | Reported with "did you mean?" suggestion |
| `OutOfRange` | Value outside `@Range` bounds | Falls back to default |
| `PatternMismatch` | String doesn't match `@Pattern` regex | Falls back to default |
| `MissingRequired` | Non-nullable field without default is absent | Error reported |

</details>

---

## 📦 Modules

| Module | Artifact | Description |
|:-------|:---------|:------------|
| `kconfig-core` | `com.github.maquqdev.KConfig:kconfig-core` | Core library. YAML, validation, annotations, migrations, file watching, `ConfigRef`. No Bukkit dependency. |
| `kconfig-bukkit` | `com.github.maquqdev.KConfig:kconfig-bukkit` | Optional Bukkit integration. Serializers for `Location`, `Vector`, `Color`, `ItemStack`. |

---

## 📖 Documentation

Full documentation is available on the **[Wiki](https://github.com/maquqdev/KConfig/wiki)**:

| Section | Topics |
|:--------|:-------|
| **[Getting Started](https://github.com/maquqdev/KConfig/wiki/Getting-Started)** | Installation, first schema, loading & saving |
| **[ConfigRef](https://github.com/maquqdev/KConfig/wiki/ConfigRef)** | Live references, delegates, `selecting {}`, change callbacks, auto-reload |
| **[Annotations](https://github.com/maquqdev/KConfig/wiki/Annotations)** | `@Comment`, `@Range`, `@Pattern`, `@Secret`, `@Env`, `@MigrateFrom`, `@Transient` |
| **[Secrets](https://github.com/maquqdev/KConfig/wiki/Secrets)** | `SecretString`, masking strategies, `@Env` overrides |
| **[Migrations](https://github.com/maquqdev/KConfig/wiki/Migrations)** | `ConfigMigration`, version chains, backup behavior |
| **[Custom Serializers](https://github.com/maquqdev/KConfig/wiki/Custom-Serializers)** | `TypeSerializer<T>`, built-in serializers |
| **[Error Handling](https://github.com/maquqdev/KConfig/wiki/Error-Handling)** | Error types, fallback behavior, "did you mean?" |
| **[File Watching](https://github.com/maquqdev/KConfig/wiki/File-Watching)** | WatchService, debounce, error safety |
| **[Bukkit Integration](https://github.com/maquqdev/KConfig/wiki/Bukkit-Integration)** | `BukkitSerializers`, Location, Vector, Color, ItemStack |

---

## 🤝 Contributing

Contributions are welcome. Please open an issue to discuss larger changes before submitting a PR.

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## 📄 License

[MIT](LICENSE)

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:6C3483,50:2980B9,100:1ABC9C&height=120&section=footer" width="100%"/>

</div>
