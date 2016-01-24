package android.distributed.ezbluetooth.routing;


import java.io.Serializable;

public class BluetoothConnexionWeight implements Serializable {
    private final long weight;

    public BluetoothConnexionWeight(long weight) {
        this.weight = weight;
    }

    public long getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BluetoothConnexionWeight that = (BluetoothConnexionWeight) o;

        return weight == that.weight;

    }

    @Override
    public int hashCode() {
        return (int) (weight ^ (weight >>> 32));
    }
}
