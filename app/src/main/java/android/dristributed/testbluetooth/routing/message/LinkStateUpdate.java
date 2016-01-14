package android.dristributed.testbluetooth.routing.message;

import android.dristributed.testbluetooth.routing.BluetoothRoutingTable;
import android.dristributed.testbluetooth.routing.SocketWrapper;
import android.dristributed.testbluetooth.routing.message.visitor.Visitor;

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
