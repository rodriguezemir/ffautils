# MiniMessage displayName Pattern

## Convention

All inventory `ItemMeta` display names MUST use MiniMessage deserialization with `TextDecoration.ITALIC, false`.

## Pattern

```java
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

meta.displayName(MiniMessage.miniMessage().deserialize("<color>Text</color>")
    .decoration(TextDecoration.ITALIC, false));
```

## Color Palette (from messages.yml)

| Element | Color | MiniMessage |
|---------|-------|-------------|
| Spawns button | Blue→Cyan gradient | `<gradient:#5472F4:#27A2C1>Spawns</gradient>` |
| Kits button | Green | `<green>Kits</green>` |
| Empty state / error | Red | `<red>No spawns configured</red>` |
| Back button | Gray | `<gray>Back</gray>` |
| Pagination (prev/next) | Gold | `<gold>Previous Page</gold>` |
| Item names (spawn/kit) | White | `<white>` + name + `</white>` |
| Info / detail labels | Gray | `<gray>Spawn Info</gray>` |

## Testing Pattern

`getDisplayName()` returns legacy color codes (`§f`) with the Component API.
Use `PlainTextComponentSerializer` to extract plain text in assertions:

```java
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

private static String plainName(ItemStack item) {
    return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
}

assertEquals("lobby", plainName(inv.getItem(0)));
```

## Do NOT

- Use `meta.setDisplayName(String)` — deprecated, use `meta.displayName(Component)`
- Use `Component.text("...")` without MiniMessage — prefer MiniMessage for consistency with messages.yml
- Forget `.decoration(TextDecoration.ITALIC, false)` — prevents unwanted italic on all items
