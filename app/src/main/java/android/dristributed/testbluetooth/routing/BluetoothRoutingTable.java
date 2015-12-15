package android.dristributed.testbluetooth.routing;


import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BluetoothRoutingTable implements RoutingTableInterface<String, SocketWrapper, BluetoothConnexionWeight>, Serializable, Cloneable {

    private Map<String, RoutingRecord<SocketWrapper, BluetoothConnexionWeight>> table = new android.support.v4.util.ArrayMap<>();

    @Override
    public RoutingRecord<SocketWrapper, BluetoothConnexionWeight> getRecord(String dest) {
        return table.get(dest);
    }

    @Override
    public RoutingRecord<SocketWrapper, BluetoothConnexionWeight> updateRoute(String dest, RoutingRecord<SocketWrapper, BluetoothConnexionWeight> record) {
        return table.put(dest, new RoutingRecord<>(record.getDoor(), record.getWeight()));
    }

    @Override
    public int getSize() {
        return table.size();
    }

    @Override
    public Iterator<String> iterator() {
        return table.keySet().iterator();
    }

    @Override
    public synchronized Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
