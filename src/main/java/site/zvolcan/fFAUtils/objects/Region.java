package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class Region {

    @Getter
    private final String world;
    @Getter
    private final int minX;
    @Getter
    private final int minY;
    @Getter
    private final int minZ;
    @Getter
    private final int maxX;
    @Getter
    private final int maxY;
    @Getter
    private final int maxZ;

    public Region(@NotNull String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = Objects.requireNonNull(world, "world");
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public Region(@NotNull Location pos1, @NotNull Location pos2) {
        this(Objects.requireNonNull(pos1.getWorld(), "pos1 world").getName(),
                pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ(),
                pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
    }

    public boolean contains(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!world.equals(location.getWorld().getName())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public long getVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
