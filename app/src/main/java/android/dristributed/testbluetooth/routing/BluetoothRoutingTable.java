package android.dristributed.testbluetooth.routing;


import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class BluetoothRoutingTable implements RoutingTableInterface<String, SocketWrapper, BluetoothConnexionWeight>, Serializable, Cloneable {

    private Map<String, RoutingRecord<SocketWrapper, BluetoothConnexionWeight>> table = new TreeMap<>();

    @Override
    public RoutingRecord<SocketWrapper, BluetoothConnexionWeight> getRecord(String dest) {
        return table.get(dest);
    }

    @Override
    public RoutingRecord<SocketWrapper, BluetoothConnexionWeight> updateRoute(String dest, @Nullable RoutingRecord<SocketWrapper, BluetoothConnexionWeight> record) {
        if (record == null) {
            return table.remove(dest);
        }
        return table.put(dest, new RoutingRecord<>(record.getDoor(), record.getWeight(), record.getUpdatedFrom()));
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
