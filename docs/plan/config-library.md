# KConfig — YAML Config Library for Bukkit/Kotlin

## Nazwa robocza: `kconfig` (albo co wolisz)

---

## Stack technologiczny

- **Język:** Kotlin 1.9+ (JVM 17+)
- **YAML parser:** SnakeYAML 2.x (już jest w Paper/Spigot)
- **Refleksja:** `kotlin-reflect` (KClass, KParameter, KType, primaryConstructor)
- **Build:** Gradle Kotlin DSL
- **Publikacja:** Maven Local → potem GitHub Packages / JitPack
- **Testy:** JUnit 5 + strikt / assertk do asercji

---

## Architektura — moduły / pakiety

```
kconfig/
├── annotation/
│   ├── Comment.kt            — @Comment(vararg lines, placement)
│   ├── CommentPlacement.kt   — enum ABOVE / INLINE
│   ├── Range.kt              — @Range(min, max)
│   ├── Pattern.kt            — @Pattern(regex, description)
│   ├── Secret.kt             — @Secret
│   ├── Env.kt                — @Env(variableName)
│   ├── MigrateFrom.kt        — @MigrateFrom(vararg oldKeys)
│   └── Transient.kt          — @Transient (pole pomijane w YAML)
│
├── serializer/
│   ├── TypeSerializer.kt     — interfejs serialize/deserialize
│   ├── SerializerRegistry.kt — mapa KClass → TypeSerializer
│   ├── BuiltinSerializers.kt — String, Int, Long, Double, Float, Boolean, Enum, List, Map
│   └── bukkit/
│       ├── ItemStackSerializer.kt
│       ├── LocationSerializer.kt
│       └── ... (opcjonalne)
│
├── error/
│   ├── ConfigError.kt        — sealed class (InvalidValue, UnknownType, UnknownKey, OutOfRange, PatternMismatch)
│   ├── ConfigErrorCollector.kt — zbiera błędy zamiast rzucać wyjątki
│   └── ConfigErrorFormatter.kt — formatuje ładny raport do loggera
│
├── migration/
│   ├── ConfigMigration.kt    — interfejs (fromVersion, toVersion, migrate(Map))
│   └── MigrationRunner.kt    — wykrywa version, chain migracji v1→v2→v3
│
├── writer/
│   ├── YamlWriter.kt         — custom YAML writer (komentarze, kolejność pól)
│   └── CommentExtractor.kt   — czyta @Comment z KParameter → Map<path, CommentData>
│
├── reader/
│   ├── YamlReader.kt         — SnakeYAML parse → Map<String, Any?>
│   ├── EnvOverrideResolver.kt — @Env overlay
│   └── Deserializer.kt       — Map → data class (refleksja + walidacja)
│
├── watcher/
│   └── FileWatcher.kt        — WatchService + debounce, opcjonalny
│
└── YamlConfigManager.kt      — główny entry point (load, save, reload, watch, registerSerializer, registerMigration)
```

---

## Fazy implementacji

### FAZA 1 — Core (MVP)
**Cel:** load/save dowolnej data classy z defaultami, zero adnotacji wymaganych.

#### 1.1 — TypeSerializer + SerializerRegistry
```kotlin
interface TypeSerializer<T> {
    fun serialize(value: T): Any    // T → prymityw / Map / List
    fun deserialize(raw: Any): T   // odwrotnie
}

class SerializerRegistry {
    private val map = ConcurrentHashMap<KClass<*>, TypeSerializer<*>>()
    fun <T : Any> register(klass: KClass<T>, serializer: TypeSerializer<T>)
    fun <T : Any> get(klass: KClass<T>): TypeSerializer<T>?
    fun has(klass: KClass<*>): Boolean
}
```

#### 1.2 — Deserializer (YAML Map → data class)
Algorytm:
1. Weź `klass.primaryConstructor`
2. Dla każdego `KParameter`:
   - Pobierz `map[param.name]`
   - Jeśli null + `param.isOptional` → skip (Kotlin wstawi default)
   - Jeśli null + required → error
   - Inaczej → `deserializeValue(raw, param.type)`
3. `deserializeValue` dispatch:
   - Prymitywy (String, Int, Long, Double, Float, Boolean) → cast z Number
   - Enum → `enumConstants.first { it.name == raw }`
   - List<T> → `raw.map { deserializeValue(it, elementType) }`
   - Map<K,V> → `raw.entries.associate { deserializeValue(k), deserializeValue(v) }`
   - Data class → rekurencja `deserialize(klass, raw as Map)`
   - Registry hit → `serializer.deserialize(raw)`
   - Brak → error
4. `constructor.callBy(args)` — Kotlin automatycznie uzupełnia defaulty

**Kluczowe:** `KType.arguments` do wyciągania generic typów List<T>, Map<K,V>.

#### 1.3 — Serializer (data class → YAML Map)
Odwrotność — rekurencja po `klass.memberProperties`:
1. Dla każdego property z primaryConstructor:
   - `serializeValue(value)` → prymityw / Map / List
2. Zwróć `LinkedHashMap` (zachowuje kolejność!)

#### 1.4 — YamlReader (plik → Map)
```kotlin
object YamlReader {
    fun read(file: File): Map<String, Any?> {
        val yaml = Yaml()  // SnakeYAML
        return file.reader().use { yaml.load(it) } ?: emptyMap()
    }
}
```

#### 1.5 — YamlWriter (Map → plik, BEZ komentarzy na razie)
Custom writer bo SnakeYAML Dumper nie daje kontroli nad formatowaniem:
- Rekurencyjny StringBuilder
- `LinkedHashMap` → pola w kolejności z data classy
- Stringi z specialnymi znakami → quotowane
- Listy → `- item` format
- Nested map → indent +2

#### 1.6 — YamlConfigManager
```kotlin
object YamlConfigManager {
    val registry = SerializerRegistry()

    inline fun <reified T : Any> load(file: File): T
    fun <T : Any> save(file: File, instance: T)
    fun <T : Any> registerSerializer(klass: KClass<T>, serializer: TypeSerializer<T>)
}
```

#### Testy fazy 1:
- Prosta data class z prymitywami — load/save roundtrip
- Nested data classy 3 poziomy głęboko
- List<String>, List<NestedDataClass>, Map<String, Int>
- Brakujące klucze → defaulty
- Nadmiarowe klucze → ignorowane (na razie)
- Plik nie istnieje → tworzony z defaultami
- Custom TypeSerializer (np. Notice)

---

### FAZA 2 — Komentarze
**Cel:** @Comment z ABOVE/INLINE, zachowanie w YAML.

#### 2.1 — Adnotacje
```kotlin
enum class CommentPlacement { ABOVE, INLINE }

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Comment(
    vararg val lines: String,
    val placement: CommentPlacement = CommentPlacement.ABOVE
)
```

#### 2.2 — CommentExtractor
```kotlin
object CommentExtractor {
    // Rekurencyjnie zbiera komentarze z data classy
    // Zwraca Map<"path.to.field", CommentData>
    fun extract(klass: KClass<*>, prefix: String = ""): Map<String, CommentData>
}

data class CommentData(
    val lines: List<String>,
    val placement: CommentPlacement,
    val children: Map<String, CommentData>  // nested sections
)
```

#### 2.3 — YamlWriter v2
Rozszerz writer o:
- Przed/po wartości wstaw komentarz
- Wieloliniowy → zawsze ABOVE
- INLINE + flat → `key: value  # comment`
- INLINE + sekcja/lista → fallback ABOVE
- Pusta linia przed sekcją z komentarzem ABOVE (czytelność)

#### Testy:
- Roundtrip: save z komentarzami → odczytaj plik → komentarze są w YAML
- INLINE na flat value
- INLINE na sekcji → fallback ABOVE
- Wieloliniowy komentarz
- Brak komentarza → czysta linia

---

### FAZA 3 — Error handling
**Cel:** inteligentny parser, zbiera wszystkie błędy, ładny raport.

#### 3.1 — ConfigError sealed class
```kotlin
sealed class ConfigError(val path: String) {
    class InvalidValue(path, val raw: Any?, val expected: String, val hint: String?)
    class UnknownType(path, val typeName: String, val rawSection: Map<String, Any?>)
    class UnknownKey(path, val suggestion: String?)  // Levenshtein match
    class OutOfRange(path, val raw: Number, val min: Double, val max: Double, val fellBackTo: Any?)
    class PatternMismatch(path, val raw: String, val pattern: String, val description: String?)
    class MissingRequired(path, val expected: String)
}
```

#### 3.2 — ConfigErrorCollector
```kotlin
class ConfigErrorCollector {
    private val errors = mutableListOf<ConfigError>()
    fun add(error: ConfigError)
    fun hasErrors(): Boolean
    fun all(): List<ConfigError>
    fun clear()
}
```

Wstrzykiwany do Deserializera — zamiast `throw`, dodaje error i zwraca null (fallback do default).

#### 3.3 — Levenshtein distance
```kotlin
fun String.levenshtein(other: String): Int
fun Collection<String>.closestMatch(input: String, maxDistance: Int = 3): String?
```

Użycie: nieznany klucz → "Did you mean X?", zły enum → "Did you mean TITLE?"

#### 3.4 — ConfigErrorFormatter
- Grupuje błędy po typie
- Formatuje z §-kolorami (Bukkit) LUB ANSI (stdout)
- Pokazuje YAML context wokół błędu
- Podsumowanie ile pól spadło do defaultów

#### Testy:
- Zły typ wartości → InvalidValue z hintem
- Nieznany klucz → UnknownKey z sugestią
- Brak serializera → UnknownType
- Wiele błędów naraz → wszystkie w raporcie
- Zero błędów → brak outputu

---

### FAZA 4 — Walidacja
**Cel:** @Range, @Pattern, walidacja po deserializacji.

#### 4.1 — Adnotacje
```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Range(val min: Double, val max: Double)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pattern(val regex: String, val description: String = "")
```

#### 4.2 — ValidationPass
Po deserializacji, przed zwróceniem obiektu:
1. Iteruj po polach
2. Sprawdź adnotacje na KParameter
3. @Range → czy wartość w przedziale? Jeśli nie → error + fallback do default
4. @Pattern → regex match? Jeśli nie → error + fallback
5. Rekurencja dla nested data class

#### Testy:
- Int w range → ok
- Int poza range → error + default
- String pasuje do regex → ok
- String nie pasuje → error + default
- @Range na non-numeric → ignorowane / warning

---

### FAZA 5 — Migracje
**Cel:** automatyczna migracja starych configów.

#### 5.1 — Wersjonowanie
Konwencja: pole `configVersion: Int = X` w głównej data classie.
Biblioteka czyta tę wartość z raw YAML przed deserializacją.

#### 5.2 — MigrationRunner
```kotlin
class MigrationRunner {
    private val migrations = mutableListOf<ConfigMigration>()

    fun register(migration: ConfigMigration)

    // Automatycznie chain: v1→v2→v3→...→current
    fun migrate(rawMap: MutableMap<String, Any?>, currentVersion: Int): MutableMap<String, Any?>
}

interface ConfigMigration {
    val fromVersion: Int
    val toVersion: Int
    fun migrate(map: MutableMap<String, Any?>): MutableMap<String, Any?>
}
```

#### 5.3 — @MigrateFrom (prosty rename)
```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class MigrateFrom(vararg val oldKeys: String)
```

W deserializerze: jeśli `map[param.name]` == null, sprawdź `map[oldKey]` dla każdego oldKey.
Jeśli znaleziono → użyj + zaloguj migrację.

#### 5.4 — Backup
Przed migracją: `file.copyTo(File("${file.name}.v${oldVersion}.bak"))`

#### Testy:
- Migracja v1→v2 (rename pola)
- Chain v1→v2→v3
- Brak migracji (config aktualny) → no-op
- Backup tworzony przed migracją

---

### FAZA 6 — @Env + @Secret + @Transient
**Cel:** produkcyjne security features.

#### 6.1 — @Env
```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Env(val variable: String)
```

Resolver po deserializacji:
```
System.getenv(variable) → jeśli istnieje, nadpisz pole
```
Priorytet: ENV > YAML > default.
**Nigdy** nie zapisuj env wartości do pliku przy save().

#### 6.2 — @Secret (szczegółowo)

##### Adnotacja
```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Secret(
    val mask: MaskStrategy = MaskStrategy.FULL,
    val visibleChars: Int = 4  // ile znaków zostawić przy PARTIAL
)

enum class MaskStrategy {
    /** "superSecret123" → "********" */
    FULL,
    /** "superSecret123" → "supe********" */
    PARTIAL,
    /** "superSecret123" → "s*************3" (pierwszy + ostatni) */
    EDGES
}
```

##### Użycie w data classie
```kotlin
data class LobbyConfig(
    @Secret
    val natsPassword: String = "",

    @Secret(mask = MaskStrategy.PARTIAL, visibleChars = 6)
    val databaseUrl: String = "jdbc:postgresql://localhost/duels",

    @Secret(mask = MaskStrategy.EDGES)
    val apiKey: String = "",
)
```

##### SecretString wrapper (opcjonalny, type-safe)
```kotlin
/**
 * Wrapper na String który NIGDY nie wypluje wartości w toString/logs.
 * Używasz go zamiast String dla pól które MUSZĄ być bezpieczne.
 */
@JvmInline
value class SecretString(private val value: String) {
    /** Jawne pobranie — musisz świadomie wywołać */
    fun expose(): String = value

    /** toString ZAWSZE maskuje */
    override fun toString(): String = "********"
}
```

Użycie:
```kotlin
data class DatabaseConfig(
    val host: String = "localhost",
    val port: Int = 5432,
    val password: SecretString = SecretString(""),
)

// w kodzie:
val conn = DriverManager.getConnection(url, user, config.password.expose())

// w logach / println / error formatter:
println(config.password)  // → "********"  (ZAWSZE bezpieczne)
```

Serializer dla SecretString:
```kotlin
object SecretStringSerializer : TypeSerializer<SecretString> {
    override fun serialize(value: SecretString): Any = value.expose()  // do YAML idzie plaintext
    override fun deserialize(raw: Any): SecretString = SecretString(raw.toString())
}
```

##### Gdzie maskowanie się odpala

**1. Error formatter** — gdy config ma błąd w secret polu:
```
  Fix these values:

    natsPassword:
      natsPassword: ********  <<<< Expected String (non-empty)
```
NIGDY nie pokazuj prawdziwej wartości w error logu, nawet jeśli jest błędna.

**2. Config dump / debug logging:**
```kotlin
// YamlConfigManager
fun <T : Any> toDebugString(instance: T): String {
    // serializuje do Map, potem maskuje @Secret pola
    val map = serialize(instance)
    val secrets = SecretExtractor.extract(instance::class)
    return maskSecrets(map, secrets).toPrettyYaml()
}
```

Wynik:
```yaml
serverId: "lobby-01"
natsUrl: "nats://localhost:4222"
natsPassword: "********"
databaseUrl: "jdbc:p********"       # PARTIAL, 6 visible
apiKey: "s*************3"           # EDGES
```

**3. Reload logging:**
```
Config reloaded! Changed fields:
  serverId: "lobby-01" → "lobby-02"
  natsPassword: ******** → ********     # nie pokazuje ani starej ani nowej
  maxBet: 10000 → 50000
```

**4. Save do YAML — BEZ maskowania:**
Zapis do pliku ZAWSZE plaintext. Maskowanie to warstwa WYŚWIETLANIA, nie storage.
Plik jest odpowiedzialnością admina (permissions, gitignore).

##### Implementacja maskowania
```kotlin
object SecretMasker {
    fun mask(value: String, strategy: MaskStrategy, visibleChars: Int): String {
        if (value.isEmpty()) return "********"

        return when (strategy) {
            MaskStrategy.FULL -> "********"

            MaskStrategy.PARTIAL -> {
                val visible = value.take(visibleChars.coerceAtMost(value.length))
                "$visible${"*".repeat(8)}"
            }

            MaskStrategy.EDGES -> {
                if (value.length <= 2) return "********"
                val first = value.first()
                val last = value.last()
                "$first${"*".repeat((value.length - 2).coerceAtLeast(6))}$last"
            }
        }
    }
}
```

##### SecretExtractor — zbiera secret metadata z klasy
```kotlin
object SecretExtractor {
    data class SecretFieldInfo(
        val path: String,
        val strategy: MaskStrategy,
        val visibleChars: Int
    )

    fun extract(klass: KClass<*>, prefix: String = ""): List<SecretFieldInfo> {
        val result = mutableListOf<SecretFieldInfo>()
        val constructor = klass.primaryConstructor ?: return result

        for (param in constructor.parameters) {
            val fullPath = if (prefix.isEmpty()) param.name!! else "$prefix.${param.name}"
            val secretAnn = param.findAnnotation<Secret>()

            if (secretAnn != null) {
                result.add(SecretFieldInfo(fullPath, secretAnn.mask, secretAnn.visibleChars))
            }

            // czy to SecretString? → automatycznie FULL mask
            if (param.type.classifier == SecretString::class) {
                result.add(SecretFieldInfo(fullPath, MaskStrategy.FULL, 0))
            }

            // rekurencja dla nested data class
            val paramKlass = param.type.classifier as? KClass<*>
            if (paramKlass?.isData == true) {
                result.addAll(extract(paramKlass, fullPath))
            }
        }
        return result
    }
}
```

##### Testy @Secret:
- `@Secret` FULL → "********"
- `@Secret(PARTIAL, 4)` na "mypassword123" → "mypa********"
- `@Secret(EDGES)` na "apikey999" → "a*******9"
- Pusty string → "********"
- Krótki string (2 znaki) EDGES → "********"
- SecretString.toString() → "********" zawsze
- SecretString.expose() → prawdziwa wartość
- Error formatter nie wypluje secretu
- Save do YAML → plaintext (nie zamaskowany!)
- Debug dump → zamaskowany
- Reload diff log → zamaskowany po obu stronach
- Nested secret (np. `database.password`) → zamaskowany z pełną ścieżką

#### 6.3 — @Transient
- Pole pomijane przy serialize (nie trafia do YAML)
- Przy deserialize → zawsze default

---

### FAZA 7 — File Watcher (opcjonalny)
**Cel:** auto-reload bez komendy.

```kotlin
class FileWatcher(
    private val file: File,
    private val debounceMs: Long = 500,
    private val onReload: (Map<String, Any?>) -> Unit
) {
    private val watchService = FileSystems.getDefault().newWatchService()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun start()   // rejestruje MODIFY event, uruchamia polling thread
    fun stop()    // cleanup
}
```

- Debounce bo edytory zapisują wielokrotnie (temp file → rename)
- Reload na OSOBNYM WĄTKU, callback na MAIN THREAD (Bukkit scheduler)
- Walidacja + error logging → jeśli nowy config ma błędy, zachowaj stary

---

### FAZA 8 — Bukkit Serializers (osobny moduł)
**Cel:** `kconfig-bukkit` artifact z gotowymi serializerami.

```kotlin
object BukkitSerializers {
    fun registerAll(manager: YamlConfigManager) {
        manager.registerSerializer(ItemStack::class, ItemStackSerializer)
        manager.registerSerializer(Location::class, LocationSerializer)
        manager.registerSerializer(Vector::class, VectorSerializer)
        manager.registerSerializer(Color::class, ColorSerializer)
        // ... inne Bukkit typy
    }
}
```

ItemStack → przez Bukkit ConfigurationSerialization (serialize/deserialize).
Location → {world, x, y, z, pitch, yaw}

---

## Struktura Gradle

```
kconfig/
├── build.gradle.kts          — root, version catalog
├── settings.gradle.kts
├── kconfig-core/
│   ├── build.gradle.kts      — kotlin, kotlin-reflect, snakeyaml, junit5
│   └── src/
├── kconfig-bukkit/
│   ├── build.gradle.kts      — depends on kconfig-core + paper-api (compileOnly)
│   └── src/
└── kconfig-test/
    ├── build.gradle.kts      — integration tests, example configs
    └── src/
```

Dlaczego dwa moduły: `kconfig-core` jest CZYSTY — zero zależności na Bukkit.
Można użyć w Velocity, standalone app, cokolwiek.
`kconfig-bukkit` dodaje serializers specyficzne dla Bukkit API.

---

## Kolejność implementacji (priorytet)

```
Tydzień 1:  FAZA 1 — Core (load/save/serialize/deserialize)
Tydzień 2:  FAZA 3 — Error handling (bez tego debugging to koszmar)
Tydzień 3:  FAZA 2 — Komentarze (UX dla adminów)
Tydzień 4:  FAZA 4 — Walidacja (@Range, @Pattern)
Tydzień 5:  FAZA 5 — Migracje
Tydzień 6:  FAZA 6 — @Env, @Secret, @Transient
Tydzień 7:  FAZA 8 — Bukkit serializers
Tydzień 8:  FAZA 7 — File watcher (nice-to-have)
```

Uwaga: error handling (faza 3) PRZED komentarzami (faza 2) — bo bez dobrych
błędów będziesz tracił czas na debugowanie czemu config się nie ładuje.

---

## Edge case'y do ogarnięcia

1. **Nullable fields:** `val something: String? = null` — trzeba obsłużyć `KType.isMarkedNullable`
2. **Listy obiektów:** `List<SimpleLocation>` — deserializeValue musi wyciągnąć element type z KType.arguments
3. **Mapy z enum key:** `Map<NoticeType, String>` — klucz mapy to enum, nie string
4. **Puste sekcje:** `economy: {}` w YAML → SnakeYAML zwraca pustą mapę, nie null
5. **Yaml anchors/aliases:** SnakeYAML obsługuje, ale uważaj na cykliczne referencje
6. **Multiline strings w YAML:** `|` i `>` block scalars — writer powinien je użyć dla długich stringów
7. **Unicode:** MiniMessage z polskimi znakami — upewnij się że writer zapisuje UTF-8
8. **Thread safety:** Config reload na main thread (Bukkit), save może być async
9. **Duże configi:** Twój LobbyMessages ma ~200 pól — refleksja musi być wydajna, cache KClass metadata
10. **Default listy/mapy:** `List<String> = listOf(...)` — Kotlin je tworzy prawidłowo ale upewnij się że deepcopy przy save
11. **camelCase → kebab-case:** Opcjonalnie: `serverId` w Kotlinie → `server-id` w YAML (dodaj flagę w load())

---

## Performance considerations

- **Cache refleksji:** Pierwsza deserializacja data classy jest wolna (KClass scanning).
  Zrób `ConcurrentHashMap<KClass<*>, ClassMetadata>` gdzie ClassMetadata trzyma:
  constructor, parameters, adnotacje, typy — odczytane RAZ.
- **Nie ładuj przy każdym uzyciu:** Load raz, trzymaj referencję. Reload jawnie.
- **Writer:** StringBuilder, nie konkatenacja stringów.
- **SnakeYAML:** Użyj jednej instancji Yaml() z reusable DumperOptions.

---

## API końcowe — jak to wygląda z perspektywy usera

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var config: LobbyConfig private set

    override fun onEnable() {
        // Bukkit serializers
        BukkitSerializers.registerAll(YamlConfigManager)

        // Custom serializers
        YamlConfigManager.registerSerializer(Notice::class, NoticeSerializer)

        // Migracje (opcjonalne)
        YamlConfigManager.registerMigration(LobbyConfig::class, object : ConfigMigration {
            override val fromVersion = 1
            override val toVersion = 2
            override fun migrate(map: MutableMap<String, Any?>) = map.apply {
                remove("oldField")
                putIfAbsent("newField", "default")
                this["configVersion"] = 2
            }
        })

        // Load — to jest CAŁY boilerplate
        config = YamlConfigManager.load<LobbyConfig>(dataFolder.resolve("config.yml"))
    }

    fun reloadConfig() {
        config = YamlConfigManager.load<LobbyConfig>(dataFolder.resolve("config.yml"))
    }
}
```

I data classy — BEZ ŻADNYCH ZMIAN:

```kotlin
data class LobbyConfig(
    @Comment("Server identifier used in NATS routing")
    val serverId: String = "lobby-01",

    @Comment("NATS connection URL")
    val natsUrl: String = "nats://localhost:4222",

    @Env("NATS_PASSWORD") @Secret
    val natsPassword: String = "",

    @Range(min = 1.0, max = 1000.0)
    val chatNatsRateLimitPerMinute: Int = 100,

    val messages: LobbyMessages = LobbyMessages()
)
```

To jest cały interfejs. Reszta to magia wewnątrz biblioteki.
