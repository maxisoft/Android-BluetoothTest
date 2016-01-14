package android.dristributed.testbluetooth.routing;

import java.io.Serializable;

public class RoutingRecord<Door, Weight> implements Serializable {
    private final Door door;
    private final Weight weight;
    private final String updatedFrom;

    public RoutingRecord(Door door, Weight weight, String updatedFrom) {
        this.door = door;
        this.weight = weight;
        this.updatedFrom = updatedFrom;
    }

    public Door getDoor() {
        return door;
    }

    public Weight getWeight() {
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

        RoutingRecord<?, ?> that = (RoutingRecord<?, ?>) o;

        if (getDoor() != null ? !getDoor().equals(that.getDoor()) : that.getDoor() != null)
            return false;
        return !(getWeight() != null ? !getWeight().equals(that.getWeight()) : that.getWeight() != null);

    }

    @Override
    public int hashCode() {
        int result = getDoor() != null ? getDoor().hashCode() : 0;
        result = 31 * result + (getWeight() != null ? getWeight().hashCode() : 0);
        return result;
    }
}
