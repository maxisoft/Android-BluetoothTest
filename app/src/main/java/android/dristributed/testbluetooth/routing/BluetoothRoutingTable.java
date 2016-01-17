package android.dristributed.testbluetooth.routing;


import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class BluetoothRoutingTable implements Serializable, Cloneable, Iterable<String> {

    private Map<String, BluetoothRoutingRecord> table = new TreeMap<>();

    public BluetoothRoutingRecord getRecord(String dest) {
        return table.get(dest);
    }

    public BluetoothRoutingRecord updateRoute(String dest, @Nullable BluetoothRoutingRecord record) {
        if (record == null) {
            return table.remove(dest);
        }
        return table.put(dest, new BluetoothRoutingRecord(record.getDoor(), record.getWeight(), record.getUpdatedFrom()));
    }

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
