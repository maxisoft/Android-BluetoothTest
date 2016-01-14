package android.dristributed.testbluetooth.routing;


import java.io.Serializable;

public class BluetoothConnexionWeight implements Serializable {
    private final long weight;

    public BluetoothConnexionWeight(long weight) {
        this.weight = weight;
    }

    public long getWeight() {
        return weight;
    }
}
