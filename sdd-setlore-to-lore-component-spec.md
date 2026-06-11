# Inventory Lore Display Specification

## Purpose

Define the requirements for rendering lore lines on inventory items using Paper's Component API with MiniMessage parsing and explicit italic control. This ensures consistent visual behavior across KitsInventory, SpawnsInventory, and SpawnDetailInventory.

## Requirements

### Requirement: MiniMessage Lore Parsing

The system SHALL parse each lore line as a MiniMessage string before converting to a Component.

#### Scenario: Plain text lore line

- GIVEN a lore line containing plain text (e.g., "world")
- WHEN the line is parsed with MiniMessage
- THEN the resulting Component SHALL contain the plain text without formatting

#### Scenario: MiniMessage formatted lore line

- GIVEN a lore line containing MiniMessage tags (e.g., "<red>2 items</red>")
- WHEN the line is parsed with MiniMessage
- THEN the resulting Component SHALL apply the specified formatting

### Requirement: Italic Decoration Disabled

The system SHALL disable italic decoration for every lore line Component.

#### Scenario: Italic default behavior

- GIVEN a lore line Component parsed from MiniMessage
- WHEN the Component is rendered in an inventory item
- THEN the text SHALL NOT appear in italic style

#### Scenario: Explicit italic override

- GIVEN a lore line containing an explicit italic tag (e.g., "<italic>true</italic>")
- WHEN the line is parsed with MiniMessage and italic decoration is set to false
- THEN the italic decoration SHALL be overridden to false

### Requirement: Empty and Null Lore Handling

The system SHALL treat null lore lists as empty lists and display no lore lines.

#### Scenario: Null lore list

- GIVEN an item meta with null lore list
- WHEN the lore is set using the Component API
- THEN no lore lines SHALL be displayed on the item

#### Scenario: Empty lore list

- GIVEN an item meta with an empty lore list
- WHEN the lore is set using the Component API
- THEN no lore lines SHALL be displayed on the item

### Requirement: Lore Content Integrity

The system SHALL derive lore content from the same data sources as the previous implementation.

#### Scenario: Kit item count

- GIVEN a kit with 3 items
- WHEN the kit is displayed in KitsInventory
- THEN the lore SHALL contain a line with "3 items"

#### Scenario: Spawn world name

- GIVEN a spawn located in world "world"
- WHEN the spawn is displayed in SpawnsInventory
- THEN the lore SHALL contain a line with "world"

#### Scenario: Allowed kits list

- GIVEN a spawn with allowed kits ["archer", "warrior"]
- WHEN the spawn is displayed in SpawnsInventory or SpawnDetailInventory
- THEN the lore SHALL contain a line with "Allowed kits: archer, warrior"

### Requirement: Test Verification

Tests SHALL verify lore content using PlainTextComponentSerializer to extract plain text from Components.

#### Scenario: Test lore assertion

- GIVEN a test that reads lore from an inventory item
- WHEN the test calls lore() on the ItemMeta
- THEN the test SHALL serialize each Component to plain text before assertion

## Non-Functional Requirements

### Compatibility

- The change MUST NOT affect the visual output of inventory items (same text, same colors).
- The change MUST use Paper's Component API and Adventure library already bundled with Paper.

### Performance

- The parsing of lore lines with MiniMessage MUST NOT introduce noticeable latency in inventory rendering.

### Testability

- All existing inventory tests MUST continue to pass after migration.
- New tests SHOULD cover edge cases of MiniMessage parsing and italic control.

## Acceptance Criteria

- [ ] All three inventory files use `lore(List<Component>)` instead of `setLore(List<String>)`.
- [ ] Every lore line is parsed with MiniMessage and has italic decoration set to false.
- [ ] No visual change in-game (same text, same colors, no italic).
- [ ] All inventory tests pass, with assertions updated to use `lore()` and PlainTextComponentSerializer.
- [ ] No null pointer exceptions when lore list is null or empty.