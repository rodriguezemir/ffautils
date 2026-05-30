package site.zvolcan.fFAUtils.objects;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable value object representing a configurable death event.
 */
public final class DeathEvent {

    private final String name;
    private final String message;
    private final boolean broadcast;
    private final EffectType effect;

    public DeathEvent(@NotNull String name, @NotNull String message,
                      boolean broadcast, EffectType effect) {
        this.name = Objects.requireNonNull(name, "name");
        this.message = Objects.requireNonNull(message, "message");
        this.broadcast = broadcast;
        this.effect = effect != null ? effect : EffectType.NONE;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public EffectType getEffect() {
        return effect;
    }
}