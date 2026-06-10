# Proposal: Configuration Inventory GUIs

## Intent

All FFAUtils configuration (spawns, kits) is currently managed via files and commands. There's no visual way to browse, verify, or manage entities. Adding a `/ffautils` GUI lets server admins inspect and navigate configuration state at a glance — reducing errors and improving UX for non-technical operators.

## Scope

### In Scope
- `/ffautils` command (no args) opens the Main config inventory GUI
- Main inventory: two buttons — "Spawns" and "Kits", each opening their respective sub-inventory
- Spawns inventory: lists all spawns from `SpawnManager.getAllSpawnsData()` with name + world info; click to see details (location, allowed kits)
- Kits inventory: lists all kits from `KitManager.getAllKits()` with name + item count; click to see kit contents preview
- Inventory click handlers for navigation (back button, sub-item clicks)
- GUI-only for this phase — read-only browsing, no create/edit/delete via GUI yet

### Out of Scope
- Creating, editing, or deleting spawns/kits via GUI (future phase)
- Permission-gated GUI sections
- Configurable inventory layouts or icon materials
- PlaceholderAPI integration in inventory titles/items

## Capabilities

### New Capabilities
- `config-inventory`: Main configuration GUI opened via `/ffautils`, navigates to sub-inventories for Spawns and Kits browsing

### Modified Capabilities
None — existing command and manager behavior stays unchanged.

## Approach

1. **Use FastInv** (`fr.mrmicky:fastinv:3.1.2`, relocated to `site.zvolcan.fFAUtils.fastinv`) — already a project dependency, provides `FastInv` holder, slot management, and click handlers out of the box.
2. **New package**: `site.zvolcan.fFAUtils.inventory/` with:
   - `ConfigMenuManager` — factory class that creates inventory instances; registered as a `@Getter` field on `FFAUtils`
   - `MainInventory` — FastInv with Spawns/Kits buttons
   - `SpawnsInventory` — paginated list of spawns
   - `KitsInventory` — paginated list of kits
3. **Modify `MainCommand`**: add a no-arg handler (or default execute) that opens the MainInventory for the player sender.
4. **Modify `CommandManager`**: pass `ConfigMenuManager` (or its dependencies) to `MainCommand`.
5. **Modify `FFAUtils.java`**: instantiate `ConfigMenuManager` in `onEnable()`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/.../commands/MainCommand.java` | Modified | Add default handler that opens MainInventory |
| `src/main/java/.../managers/CommandManager.java` | Modified | Pass ConfigMenuManager to MainCommand |
| `src/main/java/.../FFAUtils.java` | Modified | Instantiate ConfigMenuManager |
| `src/main/java/.../inventory/` | New | ConfigMenuManager, MainInventory, SpawnsInventory, KitsInventory |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| FastInv relocation path mismatch | Low | Verified in `build.gradle.kts`: `site.zvolcan.fFAUtils.fastinv` |
| Large spawn/kit counts cause pagination issues | Low | FastInv supports pagination; cap at reasonable default (e.g., 45/page) |
| Inventory open on async thread | Low | Ensure GUI opens from sync context (command handler runs sync in Paper) |

## Rollback Plan

- Remove `inventory/` package
- Revert changes to `MainCommand.java`, `CommandManager.java`, `FFAUtils.java`
- No data migration needed — purely additive UI layer

## Dependencies

- `fr.mrmicky:fastinv:3.1.2` (already in `build.gradle.kts`, shadow-relocated)
- Paper 1.21.4 Inventory API (bundled with server)

## Success Criteria

- [ ] `/ffautils` opens a 3-row inventory with Spawns and Kits buttons
- [ ] Clicking "Spawns" shows a paginated list of all configured spawns
- [ ] Clicking "Kits" shows a paginated list of all configured kits
- [ ] Back button returns to Main inventory
- [ ] Empty states display a meaningful message (no crashes on 0 spawns/kits)
