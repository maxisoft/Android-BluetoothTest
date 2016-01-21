package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.BluetoothRoutingTable;
import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

/**
 * These messages contain updated information about the state of certain links on the LSDB
 */
public class LinkStateUpdate implements RoutingMessage {

    private final BluetoothRoutingTable routingTable;

    public LinkStateUpdate(BluetoothRoutingTable routingTable) {
        this.routingTable = routingTable;
    }

    public BluetoothRoutingTable getRoutingTable() {
        return routingTable;
    }

    @Override
    public String toString() {
        return "LinkStateUpdate";
    }

    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        visitor.visit(this, from);
    }
}
