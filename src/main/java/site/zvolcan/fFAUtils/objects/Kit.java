package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Kit {

    @Getter
    private final String name;
    @Getter
    private final ItemStack[] contents;

    public Kit(@NotNull String name, @NotNull ItemStack[] contents) {
        this.name = Objects.requireNonNull(name, "name");
        this.contents = contents != null ? contents.clone() : new ItemStack[0];
    }
    public int size() {
        return contents.length;
    }
}