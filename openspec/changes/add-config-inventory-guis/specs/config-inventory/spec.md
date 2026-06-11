# Config Inventory GUI Specification

## Purpose

Read-only configuration inventory GUI opened via `/ffautils`, browsing spawns and kits with FastInv.

## Requirements

### Requirement: Main Config Inventory

The system SHALL open a 3-row FastInv inventory when `/ffautils` is executed with no arguments.

#### Scenario: Open Main Inventory

- GIVEN a player with `ffautils.commands.ffautils` permission
- WHEN the player executes `/ffautils`
- THEN a 27-slot inventory titled "FFAUtils Config" opens with a "Spawns" button (slot 11) and "Kits" button (slot 15)

#### Scenario: Non-player sender

- GIVEN a console sender
- WHEN `/ffautils` is executed
- THEN the command is rejected by the Brigadier `requires` check

#### Scenario: No permission

- GIVEN a player without `ffautils.commands.ffautils`
- WHEN `/ffautils` is executed
- THEN the permission check rejects the command

### Requirement: Spawns Sub-Inventory

The system SHALL display a paginated inventory of all configured spawns when the Spawns button is clicked.

#### Scenario: Open Spawns

- GIVEN at least one spawn exists
- WHEN the player clicks "Spawns"
- THEN a 54-slot inventory opens with each spawn showing name (display) and world (lore)
- AND a back button in slot 49 returns to Main

#### Scenario: Spawn Detail

- GIVEN the Spawns inventory is open
- WHEN a player clicks a spawn entry
- THEN a 27-slot detail view shows name, world, coordinates, and allowed kits (or "All kits")
- AND a back button in slot 22 returns to Spawns

#### Scenario: Empty Spawns

- GIVEN zero spawns configured
- WHEN the Spawns inventory opens
- THEN a placeholder item displays "No spawns configured"

#### Scenario: Spawns Pagination

- GIVEN more than 45 spawns
- WHEN viewing the Spawns inventory
- THEN pages split with forward/back navigation buttons

### Requirement: Kits Sub-Inventory

The system SHALL display a paginated inventory of all configured kits when the Kits button is clicked.

#### Scenario: Open Kits

- GIVEN at least one kit exists
- WHEN the player clicks "Kits"
- THEN a 54-slot inventory opens with each kit showing name (display) and item count (lore)
- AND a back button in slot 49 returns to Main

#### Scenario: Kit Detail

- GIVEN the Kits inventory is open
- WHEN a player clicks a kit entry
- THEN a 27-slot detail view shows name and contents as ItemStack previews
- AND a back button in slot 22 returns to Kits

#### Scenario: Empty Kits

- GIVEN zero kits configured
- WHEN the Kits inventory opens
- THEN a placeholder item displays "No kits configured"

#### Scenario: Kits Pagination

- GIVEN more than 45 kits
- WHEN viewing the Kits inventory
- THEN pages split with forward/back navigation buttons

### Requirement: Inventory Navigation

The system SHALL support bidirectional navigation between Main, Sub-inventories, and Detail views.

#### Scenario: Back from Sub-inventory

- GIVEN the player is in Spawns or Kits
- WHEN the back button is clicked
- THEN Main inventory reopens

#### Scenario: Back from Detail

- GIVEN the player is in a detail view
- WHEN the back button is clicked
- THEN the corresponding Sub-inventory reopens

#### Scenario: Close inventory

- GIVEN any inventory is open
- WHEN the player presses Escape
- THEN the inventory closes cleanly

### Requirement: ConfigMenuManager Wiring

The system SHALL instantiate ConfigMenuManager as a `@Getter` field on FFAUtils, passed through CommandManager to MainCommand.

#### Scenario: Plugin startup

- GIVEN the plugin enables
- WHEN `onEnable()` completes
- THEN `ConfigMenuManager` is accessible via `FFAUtils.getInstance().getConfigMenuManager()`

#### Scenario: MainCommand wiring

- GIVEN the plugin is enabled
- WHEN `CommandManager` constructs `MainCommand`
- THEN `MainCommand` receives `ConfigMenuManager` as a parameter

## Scope Boundaries

- Read-only browsing only. Create/edit/delete via GUI is out of scope.
- No PlaceholderAPI integration in inventories.
- No configurable layouts or icon materials.
