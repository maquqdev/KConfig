# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-19

### Added
- Core YAML serialization/deserialization via Kotlin reflection and data class primary constructors
- `YamlConfigManager` - main entry point with `load`, `save`, `reload`, `watch` API
- `TypeSerializer<T>` interface and `SerializerRegistry` for custom type support
- Built-in serializers for String, Int, Long, Double, Float, Boolean
- `@Comment` annotation with ABOVE/INLINE placement for human-readable YAML output
- `@Range(min, max)` annotation for numeric bound validation with fallback to defaults
- `@Pattern(regex)` annotation for string validation with fallback to defaults
- `@Secret` annotation with FULL, PARTIAL, EDGES masking strategies
- `SecretString` inline value class - toString() never leaks plaintext
- `@Env(variable)` annotation for environment variable overrides (ENV > YAML > default)
- `@MigrateFrom(vararg oldKeys)` for backward-compatible field renames
- `@Transient` to exclude fields from YAML serialization
- `ConfigError` sealed class with 6 error types and Levenshtein "did you mean?" suggestions
- `ConfigErrorCollector` - accumulates all errors instead of fail-fast
- `ConfigErrorFormatter` - grouped, color-coded error reports
- `MigrationRunner` - version-chained config migrations (v1→v2→v3) with automatic backup
- `FileWatcher` - WatchService-based auto-reload with debounce
- `CommentExtractor` - recursive @Comment annotation extraction
- Custom YAML writer with comment insertion, proper quoting, field ordering
- `kconfig-bukkit` module with serializers for Location, Vector, Color, ItemStack
- Comprehensive test suite (97 tests)
- KDoc documentation on all public APIs
