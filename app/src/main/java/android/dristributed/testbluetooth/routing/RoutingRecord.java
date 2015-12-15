package android.dristributed.testbluetooth.routing;

public class RoutingRecord<Door, Weight> {
    private final Door door;
    private final Weight weight;

    public RoutingRecord(Door door, Weight weight) {
        this.door = door;
        this.weight = weight;
    }

    public Door getDoor() {
        return door;
    }

    public Weight getWeight() {
        return weight;
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

        if (getDoor() != null ? !getDoor().equals(that.getDoor()) : that.getDoor() != null) return false;
        return !(getWeight() != null ? !getWeight().equals(that.getWeight()) : that.getWeight() != null);

    }

    @Override
    public int hashCode() {
        int result = getDoor() != null ? getDoor().hashCode() : 0;
        result = 31 * result + (getWeight() != null ? getWeight().hashCode() : 0);
        return result;
    }
}
