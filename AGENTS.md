# FFAUtils — Paper 1.21.4 plugin

## Build & test

```sh
./gradlew build           # compiles + runs tests + shadowJar
./gradlew test             # runs all tests
./gradlew shadowJar        # fat JAR to build/libs/
```

Gradle configuration cache is on. If you change `build.gradle.kts`, run with `--no-configuration-cache` or clear `.gradle/configuration-cache/`.

## Run a local server

```sh
./gradlew runServer        # Paper 1.21.4, 2GB RAM, auto-agrees to EULA
```

Server runs in `build/runServer/`. Plugin JAR is deployed automatically.

## Project layout

```
src/
  main/
    java/site/zvolcan/fFAUtils/
      FFAUtils.java              # main class (static singleton via @Getter)
      FFAPlaceholders.java       # PlaceholderAPI expansion
      managers/                  # business logic — stateful singletons
      commands/                  # Brigadier command implementations
      commands/abs/              # CommandExecutor functional interface
      listeners/                 # Bukkit event handlers
      objects/                   # value objects (DeathEvent, Kit, FFAPlayer, etc.)
      providers/                 # external tier APIs behind the abstract TierProvider
    resources/
      paper-plugin.yml           # plugin descriptor (NOT plugin.yml!)
      config.yml                 # main config
      messages.yml               # message strings
      death-messages.yml         # death event message pool
      spawn-lobby-items.yml      # lobby hotbar items
  test/
    java/site/zvolcan/fFAUtils/managers/   # test classes
```

## Architecture

- **Plugin descriptor**: `paper-plugin.yml` (Paper's new format). Version expanded from Gradle via `processResources`.
- **Commands**: Paper Brigadier API via `LifecycleEvents.COMMANDS`. All commands implement `CommandExecutor` functional interface returning `LiteralCommandNode<CommandSourceStack>`.
- **Managers**: instantiated in `onEnable()`, stored as `@Getter` fields on the main class. Accessed via `FFAUtils.getInstance().getXxxManager()`.
- **Persistence**:
  - SpawnManager, KitManager: **per-file JSON** via Gson (`spawns/<name>.json`, `kits/<name>.json`)
  - DeathEventManager: **YAML list** via `YamlConfiguration` (`death-messages.yml`)
  - StatsManager: **HikariCP** connection pool to SQL database
- **PlaceholderAPI**: optional dependency (`required: false`), expansion registered in `onEnable()`.
- **Tier providers**: `TierProvider` is an abstract class holding everything common to an external tier API — the async `java.net.http.HttpClient` call, Gson parsing into `TierProfile`, and a per-UUID cache with TTL and in-flight de-duplication. Subclasses only declare what differs. Every one of these differences breaks lookups **silently** if got wrong, so verify against the live API when touching them:

| | `McTiersProvider` | `PvpTiersProvider` | `EliteStormProvider` |
|---|---|---|---|
| Endpoint | `mctiers.com/api/v2/profile/{uuid}` | `pvptiers.com/api/profile/{uuid}` | `api.elitestorm.es/v2/users?nickname=` / `?uuid=` |
| Player id | path segment | path segment | **query parameter** |
| UUID form | **with** dashes | **without** (dashed → 400) | **without** (dashed → 422) |
| "No profile" | 404 | **422** | 404 (422 here means *bad query*, not unknown player) |
| Name lookup | no | no | **yes, preferred** |
| Payload | map of slug → `{tier,pos}` | same | **array of `{mode,tier:"LT4",isRetired}`** |
| Gamemode slugs | `nethop`, `vanilla` | `neth_pot`, `crystal` | numeric ids, guild-scoped |

  MCTiers and PvPTiers return the same JSON shape, so `parseProfile` has a working default they both reuse; EliteStorm overrides it. Slugs are translated between sites via `getGamemodeAliases()` — `vanilla` and `crystal` exist on only one site each and are deliberately not aliased, so a player simply has no ranking there.

  Two EliteStorm specifics worth knowing:
  - It is looked up **by nickname by default**, which is what makes the gate work on offline-mode servers where Bukkit's UUID is generated locally and never matches the Mojang UUID the other sites are keyed by. Results are still cached under the UUID.
  - Its gamemode ids are **scoped to a Discord guild**, so the id → slug vocabulary is loaded from `/v2/modes?guildId=` by the `warmUp()` lifecycle hook. `EliteStormProvider.DEFAULT_MODE_SLUGS` is only correct for `DEFAULT_GUILD_ID` and is a fallback if that call fails.

## Persistence quirks

- Spawn and kit files live in subfolders of the plugin data folder, one file per entity.
- Gson serialization for ItemStack requires `normalizeTypes()` (Integer vs Double coercion) in KitManager — do not remove.
- `death-messages.yml` is deployed via `saveResource("death-messages.yml", false)` — it will NOT overwrite existing files.

## Dependencies (relocated by Shadow)

| Library | Relocated to |
|---|---|
| `com.github.putindeer:mcdev-utils` | `site.zvolcan.fFAUtils.libs.utils` |
| `com.google.code.gson` | `site.zvolcan.fFAUtils.libs.gson` |
| `com.zaxxer:HikariCP` | `site.zvolcan.fFAUtils.libs.hikari` |
| `fr.mrmicky:fastinv` | `com.yourpackage.fastinv` |

## Testing

- **MockBukkit** (not plain Mockito) provides a mocked Paper server environment.
- Tests use `@TempDir` for filesystem isolation.
- No MockBukkit extension is used in current tests — they test YAML/JSON config logic at the unit level, not server interactions.
- Test files are in `src/test/java/` mirroring the main source tree.

## Config files

- **config.yml**: currently empty, populated at runtime by plugin defaults.
- **messages.yml**: MiniMessage format (`<gradient:...>`, `<red>`, `<green>`). Prefix uses gradient.
- **spawn-lobby-items.yml**: slot index → material, display name, command mapping.

## CI

- `.github/workflows/build.yml`: runs `shadowJar` on every push/PR (Java 21, Temurin, Gradle cache).
- Tests run transitively (shadowJar depends on build → depends on test).

## Lombok

Used throughout. Annotation processor is configured in build.gradle.kts — both `compileOnly` and `annotationProcessor` scopes.
