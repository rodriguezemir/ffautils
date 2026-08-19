# FFAUtils

A free-for-all utilities plugin for **Paper 1.21.4**: kits, multiple spawns, combat logging, stats, and spawn access gated by a player's PvP tier from MCTiers, PvPTiers or EliteStorm.

![Build](https://github.com/rodriguezemir/ffautils/actions/workflows/build.yml/badge.svg)

---

## Features

- **Kits** — create, edit and delete kits from an in-game inventory editor. Kits are stored one JSON file each.
- **Multiple spawns** — name any location as a spawn, and optionally restrict which kits may be used there.
- **Tier-gated spawns** — require a minimum PvP tier (for example *HT3 or better*) to enter a spawn, resolved from MCTiers, PvPTiers or EliteStorm. See [Tier-gated spawns](#tier-gated-spawns).
- **Combat log** — players tagged in combat are killed on quit and cannot run configured commands while tagged.
- **Lobby items** — a configurable hotbar for players in the lobby, plus a respawn item for players who died with a kit loaded.
- **Stats** — kills, deaths and K/D stored in SQLite, exposed through PlaceholderAPI.
- **Death messages** — a configurable pool of broadcast messages, with killstreak announcements.

## Requirements

| | |
|---|---|
| Server | Paper 1.21.4 (uses the Paper plugin descriptor and Brigadier command API) |
| Java | 21 |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/6245/), [packetevents](https://github.com/retrooper/packetevents) |

Both optional dependencies are soft — the plugin loads fine without them, and simply skips registering placeholders when PlaceholderAPI is absent.

## Installation

1. Download `FFAUtils-<version>.jar` from the [releases](https://github.com/rodriguezemir/ffautils/releases), or build it yourself (see [Building](#building)).
2. Drop it into `plugins/`.
3. Start the server once to generate the config files, then edit them and run `/ffautils reload`.

## Commands

Every permission defaults to operators.

### Players

| Command | Permission | Description |
|---|---|---|
| `/loadme <kit> <spawn>` | *none* | Load a kit and enter a spawn. This is where kit restrictions and the tier gate apply. |
| `/spawn` | `ffautils.commands.spawn` | Teleport back to the lobby, healed and with lobby items restored. |
| `/dead` | *none* | Kill yourself. |
| `/kit <name>` | `ffautils.commands.kit` | Apply a kit. |
| `/kit list` | `ffautils.commands.kit` | List available kits. |

### Administration

| Command | Permission | Description |
|---|---|---|
| `/kit create\|edit\|delete <name>` | `ffautils.commands.kit` | Manage kits. `create` and `edit` open the inventory editor. |
| `/ffakiteditor` | `ffautils.commands.ffakiteditor` | Open the kit editor menu. Player only. |
| `/setspawn create <name>` | `ffautils.commands.setspawn` | Save your current location as a spawn. |
| `/setspawn delete <spawn>` | `ffautils.commands.setspawn` | Delete a spawn. |
| `/setspawn allowkit <spawn> <kit>` | `ffautils.commands.setspawn` | Restrict a spawn to a kit. A spawn with no allowed kits permits all of them. |
| `/setspawn removekit <spawn> <kit>` | `ffautils.commands.setspawn` | Remove a kit from a spawn's allowed list. |
| `/ffautils reload` | `ffautils.commands.ffautils` | Reload messages, kits, spawns and death messages. Player only. |
| `/tiers …` | `ffautils.commands.tiers` | Manage the tier gate — see below. |

### Other permissions

| Permission | Effect |
|---|---|
| `ffautils.bypass-combat-block` | Run blocked commands while tagged in combat. |
| `ffautils.tiers.bypass` | Enter tier-restricted spawns regardless of rank. |

> The spawn named `lobby` is special: it is where players are sent on join and by `/spawn`. If it does not exist, the first world's spawn point is used.

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Main settings, including the whole tier gate. |
| `messages.yml` | All player-facing strings, in [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format. |
| `death-messages.yml` | Pool of death broadcast messages. |
| `blocked-commands.yml` | Command labels players cannot run while in combat. |
| `spawn-lobby-items.yml` | Lobby hotbar items and the respawn item. |
| `spawns/<name>.json` | One file per spawn (location plus allowed kits). |
| `kits/<name>.json` | One file per kit. |
| `stats.db` | SQLite database of kills and deaths. |

`messages.yml` is never overwritten on update. Strings added by a newer version are seeded automatically, so the file stays valid across upgrades.

### Main `config.yml` keys

```yml
disable-lobby-items: false      # skip giving lobby items entirely
stats-database-name: stats.db   # SQLite file inside the plugin folder
combatlog.timeout-ticks: 300    # how long a combat tag lasts, in ticks
```

Combat log duration may also be given in seconds as `duration-combat-log`, which is multiplied by 20. If neither key is present the default is 15 seconds.

## Tier-gated spawns

Requires a minimum PvP tier to enter a spawn. Ranks follow the convention all three sites share: **tier 1–5 where lower is better**, plus a **position — `0` for high, `1` for low**. So `tier: 3, pos: 0` means *HT3 or better*, which lets HT3 in and turns LT3 away.

The gate is off until you turn it on:

```
/tiers                        # state, providers and restricted spawns
/tiers on | off | toggle      # enable or disable, persisted to config.yml
/tiers reload                 # re-read the tiers config section
/tiers cache clear            # drop every cached tier
/tiers check <player> [provider]
```

### Configuration

```yml
tiers:
  enabled: false
  provider: "mctiers"     # mctiers | pvptiers | elitestorm | any
  gamemode: "vanilla"     # or "best" to use the player's strongest gamemode
  tier: 3                 # 1-5, lower is stricter
  pos: 0                  # 0 = high, 1 = low

  restricted-spawns:
    tryhard:              # each spawn may override tier, pos, gamemode, provider
      tier: 3
      pos: 0
      provider: "mctiers"

  use-peak-when-retired: true   # judge retired players on their peak tier
  allow-on-error: true          # unreachable API: true lets players in, false blocks
  cache-minutes: 30             # tiers are cached per UUID, per provider
  prefetch-on-join: true        # so the check at /loadme costs no request
  bypass-permission: "ffautils.tiers.bypass"
```

Spawns not listed under `restricted-spawns` are never gated.

### Providers

Every lookup is cached per UUID per provider, prefetched when the player joins, and kept across reconnects, so the gate normally resolves without any request at all.

| | MCTiers | PvPTiers | EliteStorm |
|---|---|---|---|
| Endpoint | `mctiers.com/api/v2` | `pvptiers.com/api` | `api.elitestorm.es/v2` |
| Looked up by | UUID **with** dashes | UUID **without** dashes | **nickname** (or UUID without dashes) |
| Gamemodes | `vanilla` `sword` `axe` `pot` `nethop` `uhc` `smp` `mace` | `sword` `axe` `pot` `neth_pot` `uhc` `smp` `mace` `crystal` | `vanilla` `sword` `axe` `pot` `nethop` `uhc` `smp` `mace` |

Equivalent gamemode slugs are translated automatically between sites, so `nethop` matches PvPTiers' `neth_pot`. A gamemode that only exists on one site (`vanilla` on MCTiers and EliteStorm, `crystal` on PvPTiers) simply yields no ranking elsewhere, and the player is turned away by that provider.

**`provider: any`** checks every site and lets the player in if *any* of them allows it — useful because the sites genuinely disagree about the same player. A site that actually answered always outranks one that could not be reached, so `allow-on-error` only applies when no provider answered at all.

**EliteStorm and offline-mode servers.** EliteStorm is looked up by nickname by default, which is what makes the gate work on offline-mode servers: there the UUID Bukkit hands out is generated locally and never matches the Mojang UUID the other sites are keyed by. Set `providers.elitestorm.lookup-by: uuid` to use the UUID instead. Its gamemode ids are scoped to a Discord guild, so set `providers.elitestorm.guild-id` if you use a different one — the plugin reads the vocabulary from the API at startup.

### Threading

Every lookup runs on a dedicated thread pool; nothing HTTP-related touches the main thread, including startup and reload. If a site is slow or down, the server keeps ticking.

## PlaceholderAPI

| Placeholder | Value |
|---|---|
| `%ffa_kills%` | Player's kills |
| `%ffa_deaths%` | Player's deaths |
| `%ffa_kdr%` | Kill/death ratio |
| `%ffa_kit_<name>_players%` | Players currently in FFA using that kit |

## Building

```sh
./gradlew shadowJar   # fat JAR into build/libs/
./gradlew test        # run the test suite
./gradlew runServer   # Paper 1.21.4 test server with the plugin deployed
```

Gradle's configuration cache is enabled. If you change `build.gradle.kts`, run with `--no-configuration-cache` or clear `.gradle/configuration-cache/`.

See [AGENTS.md](AGENTS.md) for the project layout, architecture notes and the details each tier provider depends on.

## Known issues

- `time-combat-log` in the default `config.yml` is not read by anything. Combat log duration comes from `combatlog.timeout-ticks` or `duration-combat-log` — see [Main `config.yml` keys](#main-configyml-keys).

## License

[MIT](LICENSE)
