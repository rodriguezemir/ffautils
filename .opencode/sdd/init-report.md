# SDD Init Report — FFAUtils

**Generated**: 2026-06-10T13:13:00Z
**Agent**: sdd-init
**Status**: completed

---

## 1. Project Identity

| Field | Value |
|---|---|
| **Name** | FFAUtils |
| **Type** | Minecraft Paper 1.21.4 Plugin |
| **Group** | `site.zvolcan` |
| **Version** | `0.0.2-DEV` |
| **Java** | 21 |
| **Build** | Gradle (Kotlin DSL), Configuration Cache enabled, Parallel execution |
| **Plugin Descriptor** | `paper-plugin.yml` (Paper's new format) |
| **Main Class** | `site.zvolcan.fFAUtils.FFAUtils` |
| **Root dir** | `C:\Users\emir4\IdeaProjects\FFAUtils` |

## 2. Stack & Dependencies

### Compile/Implementation

| Dependency | Version | Scope |
|---|---|---|
| Paper API | 1.21.4-R0.1-SNAPSHOT | `compileOnly` |
| Lombok | 1.18.32 | `compileOnly` + annotation processor |
| PlaceholderAPI | 2.12.2 | `compileOnly` (optional at runtime) |
| mcdev-utils | 1.0.28 | `implementation` (relocated) |
| Gson | 2.14.0 | `implementation` (relocated) |
| HikariCP | 7.0.2 | `implementation` (relocated) |
| FastInv | 3.1.2 | `implementation` (relocated) |

### Test

| Dependency | Version |
|---|---|
| JUnit Jupiter | 5.10.2 |
| Mockito Core | 5.12.0 |
| Mockito JUnit Jupiter | 5.12.0 |
| MockBukkit | 4.110.0 (mockbukkit-v1.21) |
| Paper API (test) | 1.21.11-R0.1-SNAPSHOT |
| PlaceholderAPI (test) | 2.12.2 |

### Build Plugins

| Plugin | Version | Purpose |
|---|---|---|
| `com.gradleup.shadow` | 9.4.2 | Fat JAR + relocation |
| `xyz.jpenilla.run-paper` | 3.0.2 | Local server runner |

## 3. Testing Capabilities

### Test Runner
- **JUnit 5 Platform** via `useJUnitPlatform()`
- **Command**: `./gradlew test` (also runs transitively via `build` and `shadowJar`)

### Test Files Found: 7

| Test File | Package | Type | Key Patterns |
|---|---|---|---|
| `DeathEventManagerTest.java` | `managers` | YAML serialization round-trip | `@TempDir`, `YamlConfiguration`, pure file I/O |
| `SpawnManagerTest.java` | `managers` | YAML + JSON serialization + SpawnData API | `@TempDir`, `Gson`, `@Mock World`, case normalization |
| `PlayersManagerTest.java` | `managers` | Player lifecycle (create/remove/recreate) | `@Mock Player`, instance identity checks |
| `FFAPlayerTest.java` | `objects` | Value object (killstreak defaults) | Pure unit, no mocks |
| `LoadMeCommandTest.java` | `commands` | Kit containment logic | Static method import, pure boolean logic |
| `PlayerDeathListenerTest.java` | `listeners` | Killstreak, milestones, broadcast logic | `@Mock`, `MockitoAnnotations`, `mockStatic(FFAUtils.class)` |
| `CombatDamageIntegrationTest.java` | `listeners` | Full event dispatch via MockBukkit | `MockBukkit.mock()`, `ServerMock`, `EntityDamageByEntityEvent` |

### Testing Patterns Observed
1. **Pure unit** (no mocks): `FFAPlayerTest`, `LoadMeCommandTest`
2. **File-system isolated**: `DeathEventManagerTest`, `SpawnManagerTest` – use `@TempDir`, test YAML/JSON read/write independently
3. **Mockito-only**: `PlayersManagerTest`, `PlayerDeathListenerTest` – mock Bukkit entities, verify interactions
4. **MockBukkit integration**: `CombatDamageIntegrationTest` – full server mock, event dispatch via `server.getPluginManager().callEvent()`
5. **No MockBukkit extension/plugin in use** — all setup manual (`@BeforeEach`/`@AfterEach`)
6. **Test source root mirrors main**: `src/test/java/site/zvolcan/fFAUtils/`

### Test Metrics (estimated)
- Total test methods: ~90
- Pure logic tests: ~15
- Serialization tests: ~55
- Mock-based tests: ~15
- Integration tests: ~5

## 4. Architecture Overview

### Package Layout
```
site.zvolcan.fFAUtils/
├── FFAUtils.java            — Main plugin (static @Getter singleton)
├── FFAPlaceholders.java     — PlaceholderAPI expansion
├── managers/                — Stateful singletons (Spawn, Kit, CombatLog, Lobby, Players, DeathEvent, Command, Stats, Messages)
├── commands/                — Brigadier command implementations
├── commands/abs/            — CommandExecutor functional interface
├── listeners/               — Bukkit event handlers (PlayerConnect, PlayerDeath)
└── objects/                 — Value objects (DeathEvent, Kit, FFAPlayer, PlayerState, Sounds, EffectType)
```

### Persistence Patterns
- **SpawnManager, KitManager**: Per-file JSON via Gson in `spawns/<name>.json`, `kits/<name>.json`
- **DeathEventManager**: YAML list in `death-messages.yml` via `YamlConfiguration`
- **StatsManager**: HikariCP connection pool to SQL database

### Key Conventions
- Lombok `@Getter` on all manager fields in main class
- Access via `FFAUtils.getInstance().getXxxManager()`
- Command system: Paper Brigadier API via `LifecycleEvents.COMMANDS`
- All managers instantiated in `onEnable()` in dependency order
- Shadow relocation for all runtime dependencies

## 5. Strict TDD Mode

**strict_tdd: true**

Rationale:
- Project has 7 test files with ~90 test methods
- Clear test command: `./gradlew test`
- Build pipeline runs tests transitively
- Existing tests cover pure logic, serialization, mocks, and integration
- CI (referenced in AGENTS.md) runs `shadowJar` which depends on `build` which depends on `test`

## 6. Risks & Concerns

| Risk | Description |
|---|---|
| **No CI file found** | AGENTS.md references `.github/workflows/build.yml` but file does not exist on disk. May have been deleted or is untracked. |
| **Configuration Cache** | `org.gradle.configuration-cache=true` — changes to `build.gradle.kts` require `--no-configuration-cache` or cache clear. Pain point during SDD dependency changes. |
| **HikariCP test gap** | StatsManager uses HikariCP but no tests cover database operations. Any DB schema change is untested. |
| **No Checkstyle/Spotless** | No code formatting or style enforcement. Risk of drift across PRs. |
| **fastinv relocation** | Relocation target is `com.yourpackage.fastinv` (placeholder). May need updating. |
| **Paper API test version** | Test scope uses `1.21.11-R0.1-SNAPSHOT` while compileOnly uses `1.21.4-R0.1-SNAPSHOT`. Risk of API incompatibility if 1.21.11 methods are used in tests. |

## 7. Next Recommended Phase

**propose** — The project is already well-established with clear architecture and tests. The next SDD phase should be `propose` to define the scope of the desired change before design and implementation.

---

## Artifacts Persisted

| Artifact | Path |
|---|---|
| Init Report | `.opencode/sdd/init-report.md` |
