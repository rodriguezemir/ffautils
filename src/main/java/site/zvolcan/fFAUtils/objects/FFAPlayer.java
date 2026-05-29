package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public final class FFAPlayer {

    @Getter
    private final UUID uuid;
    @Getter
    @Setter
    private String lastKit = null;

    public FFAPlayer(UUID uuid) {
        this.uuid = uuid;
    }


}
