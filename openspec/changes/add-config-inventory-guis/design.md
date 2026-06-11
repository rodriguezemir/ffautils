# Design: Configuration Inventory GUIs

## Technical Approach

Add a read-only GUI layer on top of existing `SpawnManager` and `KitManager` using FastInv. A new `ConfigMenuManager` factory creates inventory instances. The `/ffautils` command (no-arg default) opens the Main inventory. Navigation is handled via FastInv click callbacks that open sibling inventories.

## Architecture Decisions

### Decision: ConfigMenuManager as factory vs. static inventory classes

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Factory class (`ConfigMenuManager`) | Extra class, but centralizes inventory creation, holds references to managers, enables future DI | **Chosen** — follows existing manager pattern in the project |
| Static inventory methods | Simpler, but scatters manager dependencies across static contexts | Rejected — breaks the project's singleton-manager convention |

### Decision: FastInv for inventory management

| Option | Tradeoff | Decision |
|--------|----------|----------|
| FastInv (`fr.mrmicky:fastinv`) | Already a dependency, handles slots/clicks/pagination elegantly | **Chosen** — zero new dependencies |
| Raw Bukkit Inventory API | No dependency, but verbose boilerplate for click handlers and item builders | Rejected — FastInv is already shaded and relocated |

### Decision: Inventory class hierarchy

| Option | Tradeoff | Decision |
|--------|----------|----------|
| One class per inventory (MainInventory, SpawnsInventory, KitsInventory, SpawnDetailInventory, KitDetailInventory) | 5 classes, each self-contained, easy to reason about | **Chosen** — clear separation, each file < 100 lines |
| Single ConfigMenuManager with switch/if logic | Fewer files, but monolithic and hard to extend | Rejected — violates single responsibility |

### Decision: Pagination strategy

| Option | Tradeoff | Decision |
|--------|----------|----------|
| 45 items per page in 54-slot inventory | Leaves 9 slots for header row + nav buttons | **Chosen** — spec says >45 triggers pagination |
| 27 items per page in 3-row inventory | Less data per page, simpler layout | Rejected — wastes screen real estate |

## Data Flow

```
Player types /ffautils
       │
       ▼
MainCommand.execute() ──→ requires("ffautils.commands.ffautils")
       │
       ▼ (no args = default handler)
ConfigMenuManager.openMain(player)
       │
       ▼
MainInventory (FastInv, 27 slots)
  ├── Slot 11: "Spawns" ──→ ConfigMenuManager.openSpawns(player, page=0)
  └── Slot 15: "Kits"   ──→ ConfigMenuManager.openKits(player, page=0)
       │
       ▼
SpawnsInventory / KitsInventory (FastInv, 54 slots)
  ├── Slot 49: Back ──→ ConfigMenuManager.openMain(player)
  ├── Slot 50: Prev page (if page > 0)
  ├── Slot 52: Next page (if more items)
  └── Click on item ──→ ConfigMenuManager.openSpawnDetail / openKitDetail
       │
       ▼
DetailView (FastInv, 27 slots)
  ├── Slot 22: Back ──→ openSpawns / openKits (same page)
  └── Display info: name, location, contents
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/site/zvolcan/fFAUtils/inventory/ConfigMenuManager.java` | Create | Factory holding SpawnManager/KitManager refs, methods: openMain, openSpawns, openKits, openSpawnDetail, openKitDetail |
| `src/main/java/site/zvolcan/fFAUtils/inventory/MainInventory.java` | Create | 27-slot FastInv with Spawns/Kits buttons |
| `src/main/java/site/zvolcan/fFAUtils/inventory/SpawnsInventory.java` | Create | 54-slot paginated spawn list with back/nav buttons |
| `src/main/java/site/zvolcan/fFAUtils/inventory/KitsInventory.java` | Create | 54-slot paginated kit list with back/nav buttons |
| `src/main/java/site/zvolcan/fFAUtils/inventory/SpawnDetailInventory.java` | Create | 27-slot detail view for a single spawn |
| `src/main/java/site/zvolcan/fFAUtils/inventory/KitDetailInventory.java` | Create | 27-slot detail view for a single kit |
| `src/main/java/site/zvolcan/fFAUtils/FFAUtils.java` | Modify | Add `@Getter ConfigMenuManager configMenuManager` field, instantiate in `onEnable()` |
| `src/main/java/site/zvolcan/fFAUtils/managers/CommandManager.java` | Modify | Accept `ConfigMenuManager` param, pass to `MainCommand` |
| `src/main/java/site/zvolcan/fFAUtils/commands/MainCommand.java` | Modify | Add `ConfigMenuManager` param, add default (no-arg) handler that opens MainInventory |

## Interfaces / Contracts

```java
// ConfigMenuManager — factory, instantiated once in onEnable()
public class ConfigMenuManager {
    private final SpawnManager spawnManager;
    private final KitManager kitManager;

    public ConfigMenuManager(SpawnManager spawnManager, KitManager kitManager);
    public void openMain(Player player);
    public void openSpawns(Player player, int page);
    public void openKits(Player player, int page);
    public void openSpawnDetail(Player player, String spawnName);
    public void openKitDetail(Player player, String kitName);
}

// Each inventory class takes ConfigMenuManager + relevant data in constructor
// FastInv click handlers call back into ConfigMenuManager for navigation
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | ConfigMenuManager creates correct inventory sizes | MockBukkit Player, verify openInventory called with correct size |
| Unit | Pagination math (page bounds, item slicing) | Pure JUnit — no server needed |
| Unit | Empty state handling (0 spawns/kits) | MockBukkit — verify placeholder item present |
| Integration | /ffautils opens MainInventory for player | MockBukkit command dispatch + inventory verification |
| E2E | Manual: click Spawns → detail → back → Kits | Manual testing on dev server |

## Migration / Rollout

No migration required. Purely additive UI layer — no data model changes, no config changes, no database changes.

## Open Questions

- [ ] Should spawn detail show coordinates as raw doubles or rounded to 1 decimal? (Recommend: 1 decimal for readability)
- [ ] Kit detail: show actual ItemStack icons or just names + count? (Recommend: actual icons per spec)
