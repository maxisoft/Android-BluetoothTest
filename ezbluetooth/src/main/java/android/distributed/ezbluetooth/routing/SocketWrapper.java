package android.distributed.ezbluetooth.routing;

import android.bluetooth.BluetoothSocket;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SocketWrapper implements Serializable {
    private transient BluetoothSocket socket;

    public SocketWrapper(@NonNull BluetoothSocket socket) {
        this.socket = socket;
    }

    @NonNull
    public BluetoothSocket getUnderlayingSocket() {
        return socket;
    }

    public String getRemoteMac() {
        return socket.getRemoteDevice().getAddress();
    }

    public void send(Serializable obj) throws IOException {
        new ObjectOutputStream(socket.getOutputStream()).writeObject(obj);
    }

    public void close() throws IOException {
        socket.close();
    }
}
