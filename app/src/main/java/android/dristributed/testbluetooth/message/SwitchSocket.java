package android.dristributed.testbluetooth.message;

import android.support.annotation.NonNull;

import java.util.UUID;

public class SwitchSocket extends AbstractMessage {
    private final UUID uuid;

    public SwitchSocket(@NonNull UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }
}
