package android.distributed.ezbluetooth.routing;


import java.io.Serializable;

public class BluetoothRoutingRecord implements Serializable {

    static final long serialVersionUID = 2L;

    private final transient SocketWrapper door;
    private final BluetoothConnexionWeight weight;
    private final String updatedFrom;

    public BluetoothRoutingRecord(SocketWrapper door, BluetoothConnexionWeight weight, String updatedFrom) {
        this.door = door;
        this.weight = weight;
        this.updatedFrom = updatedFrom;
    }

    public SocketWrapper getDoor() {
        return door;
    }

    public BluetoothConnexionWeight getWeight() {
        return weight;
    }

    public String getUpdatedFrom() {
        return updatedFrom;
    }

    @Override
    public String toString() {
        return "RoutingRecord{" +
                "door=" + door +
                ", weight=" + weight +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BluetoothRoutingRecord that = (BluetoothRoutingRecord) o;

        if (door != null ? !door.equals(that.door) : that.door != null) return false;
        if (!weight.equals(that.weight)) return false;
        return !(updatedFrom != null ? !updatedFrom.equals(that.updatedFrom) : that.updatedFrom != null);

    }

    @Override
    public int hashCode() {
        int result = door != null ? door.hashCode() : 0;
        result = 31 * result + weight.hashCode();
        result = 31 * result + (updatedFrom != null ? updatedFrom.hashCode() : 0);
        return result;
    }
}
