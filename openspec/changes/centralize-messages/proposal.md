# Proposal: Centralize All Messages

## Intent

16 message strings remain hardcoded across 6 files, while 12 are already centralized via `MessagesManager`/`messages.yml`. This inconsistency forces code changes for simple text edits, spreads duplicate strings, and leaves a typo unfixed ("Your are" → "You're"). Unifying all messages under `MessagesManager` makes the plugin fully configurable and eliminates technical debt.

## Scope

### In Scope
- Add 14 new message keys to `messages.yml` for all hardcoded strings
- Refactor 6 files to use `MessagesManager.getInstance().getMessage()` instead of inline strings
- Fix typo in CombatLogManager ("Your are" → "You're are")
- Unify 2 duplicate "kit not found" call sites that are hardcoded but already have a `kit-not-found` key
- Keep existing `utils.message()` / `utils.broadcast()` call patterns — only swap the string argument

### Out of Scope
- Modifying external `PluginUtils` API signatures (mcdev-utils library)
- Merging `death-messages.yml` into `messages.yml`
- Adding new sound-handling logic to MessagesManager
- Changing message content/format beyond the typo fix
- Adding locale/i18n support

## Capabilities

### New Capabilities
None — pure refactor, no behavioral change.

### Modified Capabilities
None — no spec-level behavior changes.

## Approach

**Pattern**: Copy the existing centralized pattern. Sound stays at the call site, text moves to `messages.yml`.

```
// Before
plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Kit '" + name + "' not found.");

// After
plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
    MessagesManager.getInstance().getMessage("kit-not-found", "{name}", name));
```

**New message keys** (14 keys): `kit-applied`, `kit-created`, `kit-edited`, `kit-already-exists`, `kit-deleted`, `kits-list`, `combat-enter`, `combat-exit`, `reload-success`, `killstreak-lost`, `killstreak-gained`, `spawn-kit-not-found`, `loadme-kit-not-found`, `loadme-spawn-not-found`.

**Reuse existing keys**: `kit-not-found` (replace 2 hardcoded duplicates in KitCommand), `no-kits-available` (already centralized).

**Files changed**:

| File | New calls | Reuse/remove |
|------|-----------|--------------|
| `KitCommand.java` | 5 new keys | Reuse `kit-not-found` ×2 |
| `LoadMeCommand.java` | 2 new keys | — |
| `SetSpawnCommand.java` | 1 new key | — |
| `CombatLogManager.java` | 2 new keys | Fix typo |
| `MainCommand.java` | 1 new key | — |
| `PlayerDeathListener.java` | 2 new keys | — |

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/resources/messages.yml` | Modified | Add 14 keys |
| `commands/KitCommand.java` | Modified | 8 lines → MessagesManager |
| `commands/LoadMeCommand.java` | Modified | 2 lines → MessagesManager |
| `commands/SetSpawnCommand.java` | Modified | 1 line → MessagesManager |
| `commands/MainCommand.java` | Modified | 1 line → MessagesManager |
| `managers/CombatLogManager.java` | Modified | 2 lines → MessagesManager + typo fix |
| `listeners/PlayerDeathListener.java` | Modified | 2 lines → MessagesManager |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Placeholder name mismatch across keys | Low | Review all `{placeholder}` usage for consistency; existing centralized keys use `{name}`, `{kit}`, `{spawn}` — match those |
| `messages.yml` overwrites user customizations | Low | `saveResource("messages.yml", false)` — will NOT overwrite; users must manually merge new keys |
| build.gradle.kts test breakage | Low | Run `./gradlew build` after; tests are YAML/JSON unit tests, unaffected |

## Rollback Plan

- Revert all 6 Java files via `git checkout`
- Remove the 14 new keys from `messages.yml` (if committed)
- No data migration — purely string relocation

## Dependencies

None — MessagesManager and PluginUtils already in use.

## Success Criteria

- [ ] All 16 hardcoded messages replaced with `MessagesManager.getInstance().getMessage(...)`
- [ ] `./gradlew build` passes with zero regressions
- [ ] Typo "Your are" corrected to "You're are"
- [ ] Existing centralized messages unchanged (12 keys verified)
- [ ] `messages.yml` contains all 14 new keys with valid MiniMessage

## Open Questions

**@product/owner — need decisions:**

1. **death-messages.yml merge?** Currently death/killstreak messages live in a separate YAML file managed by `DeathEventManager`. Should this be merged into `messages.yml` under `MessagesManager`? (Pro: single config. Con: different lifecycle — death messages use a list/random-pick system.)

2. **Sound coupling strategy?** Should sound selection move into `messages.yml` (e.g., key maps to text+sound pair), or stay at the call site as today? (Current approach: stay at call site — simpler, no API change.)

3. **Killstreak milestone messages?** The killstreak system broadcasts at milestone thresholds (e.g., 5, 10, 15 kills). Should these remain free-text or become templated with `{milestone}`?
