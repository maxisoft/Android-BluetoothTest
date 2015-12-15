package android.dristributed.testbluetooth.routing;


public interface RoutingTableInterface<Target, Door, Weight> extends Iterable<Target> {
    RoutingRecord<Door, Weight> getRecord(Target dest);

    RoutingRecord<Door, Weight> updateRoute(Target dest, RoutingRecord<Door, Weight> record);

    int getSize();
}
