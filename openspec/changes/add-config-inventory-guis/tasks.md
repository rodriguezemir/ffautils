# Tasks: Agrega inventarios de configuración, que se abran con "/ffautils". Crea un inventario Main, Spawns y Kits.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~780 (6 new files + 3 modified + tests) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 → PR 2 → PR 3 → PR 4 |
| Delivery strategy | single-pr-default (keep change in one PR) |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | ConfigMenuManager + MainInventory + wiring | PR 1 | Base: main; includes FFAUtils, CommandManager, MainCommand modifications |
| 2 | SpawnsInventory + SpawnDetailInventory | PR 2 | Base: PR 1 branch; depends on ConfigMenuManager |
| 3 | KitsInventory + KitDetailInventory | PR 3 | Base: PR 2 branch; depends on ConfigMenuManager |
| 4 | Unit and integration tests | PR 4 | Base: PR 3 branch; covers all inventory classes |

## Phase 1: Foundation / Infrastructure

- [x] 1.1 Create `site.zvolcan.fFAUtils.inventory.ConfigMenuManager` class with constructor accepting `SpawnManager` and `KitManager`, and stub methods: `openMain(Player)`, `openSpawns(Player, int)`, `openKits(Player, int)`, `openSpawnDetail(Player, String)`, `openKitDetail(Player, String)`.
- [x] 1.2 Create `site.zvolcan.fFAUtils.inventory.MainInventory` class extending `FastInv` (27 slots) with "Spawns" button at slot 11 and "Kits" button at slot 15; click handlers call `ConfigMenuManager.openSpawns` and `openKits`.
- [x] 1.3 Modify `FFAUtils.java`: add `@Getter ConfigMenuManager configMenuManager` field; instantiate in `onEnable()` after `spawnManager` and `kitManager` are ready.
- [x] 1.4 Modify `CommandManager.java`: accept `ConfigMenuManager` parameter; pass to `MainCommand` constructor.
- [x] 1.5 Modify `MainCommand.java`: accept `ConfigMenuManager` parameter; add default (no-arg) handler that calls `configMenuManager.openMain(player)` for player senders.

## Phase 2: Core Implementation

- [x] 2.1 Create `SpawnsInventory` (54-slot FastInv) with pagination (45 items/page). Render each spawn as item with display name = spawn name, lore = world name. Include back button at slot 49, prev/next page buttons at slots 50/52.
- [x] 2.2 Create `SpawnDetailInventory` (27-slot FastInv) showing spawn name, world, coordinates (x, y, z rounded to 1 decimal), and allowed kits (or "All kits"). Back button at slot 22 returns to SpawnsInventory (same page).
- [x] 2.3 Create `KitsInventory` (54-slot FastInv) with pagination (45 items/page). Render each kit as item with display name = kit name, lore = item count. Include back button at slot 49, prev/next page buttons at slots 50/52.
- [x] 2.4 Create `KitDetailInventory` (27-slot FastInv) showing kit name and contents as ItemStack previews (actual icons). Back button at slot 22 returns to KitsInventory (same page).
- [x] 2.5 Implement navigation callbacks in `ConfigMenuManager`: `openSpawns` creates `SpawnsInventory` and opens for player; `openKits` creates `KitsInventory`; `openSpawnDetail` creates `SpawnDetailInventory`; `openKitDetail` creates `KitDetailInventory`.

## Phase 3: Integration / Wiring

- [x] 3.1 Ensure `MainCommand` default handler only opens inventory for player senders (reject console with message).
- [x] 3.2 Verify inventory opens from sync context (command handler runs sync in Paper).
- [x] 3.3 Test navigation flow: Main → Spawns → detail → back → Kits → detail → back → Main.

## Phase 4: Testing

- [x] 4.1 Write unit tests for `ConfigMenuManager`: verify inventory creation sizes, pagination math (page bounds, item slicing), empty state handling (0 spawns/kits).
- [x] 4.2 Write unit tests for `SpawnsInventory` and `KitsInventory`: verify item rendering, pagination buttons visibility.
- [x] 4.3 Write integration test for `/ffautils` command: MockBukkit command dispatch, verify MainInventory opens for player with permission.
- [x] 4.4 Write integration test for navigation callbacks: verify click handlers open correct inventories.

## Phase 5: Cleanup / Documentation

- [x] 5.1 Update any existing comments or documentation referencing command usage.
- [x] 5.2 Remove any temporary debugging code.