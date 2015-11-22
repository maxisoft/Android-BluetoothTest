package android.dristributed.testbluetooth.bluetooth;


import java.util.UUID;

public class ServiceUuidGenerator {
    private final UUID baseUuid;

    public ServiceUuidGenerator(UUID baseUuid) {
        this.baseUuid = baseUuid;
    }

    public UUID getBaseUuid() {
        return baseUuid;
    }

    public UUID generate(String deviceMac){
        long deviceMacAsLong = Long.parseLong(deviceMac.replaceAll(":", ""), 16);
        return generate(deviceMacAsLong);
    }

    public UUID generate(long deviceMac){
        long leastSignificantBits = baseUuid.getLeastSignificantBits() ^ deviceMac;
        return new UUID(baseUuid.getMostSignificantBits(), leastSignificantBits);
    }
}
