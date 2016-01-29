package android.distributed.ezbluetooth.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Discoverable {

    public static void makeDiscoverable(@NonNull Context context, @NonNull BluetoothAdapter bluetoothAdapter, int timeOut) {
        try {
            makeDiscoverableUsingReflexion(bluetoothAdapter, timeOut);
        } catch (Exception e) {
            Log.i(Discoverable.class.getSimpleName(), "cannot use reflexion to make device discoverable");
            makeDiscoverableUsingIntent(context, timeOut);
        }
    }

    private static void makeDiscoverableUsingReflexion(@NonNull BluetoothAdapter bluetoothAdapter, int timeOut)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method setScanModeMethod = BluetoothAdapter.class.getDeclaredMethod("setScanMode", int.class, int.class);
        setScanModeMethod.invoke(bluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, timeOut);
    }

    private static void makeDiscoverableUsingIntent(@NonNull Context context, int timeOut) {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeOut);
        context.startActivity(discoverableIntent);
    }
}
