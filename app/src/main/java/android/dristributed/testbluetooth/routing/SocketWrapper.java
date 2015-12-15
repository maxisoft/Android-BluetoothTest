package android.dristributed.testbluetooth.routing;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.NonNull;

public class SocketWrapper {
    private BluetoothSocket socket;

    public SocketWrapper(@NonNull BluetoothSocket socket) {
        this.socket = socket;
    }


    public @NonNull BluetoothSocket getUnderlayingSocket(){
        return socket;
    }
}
