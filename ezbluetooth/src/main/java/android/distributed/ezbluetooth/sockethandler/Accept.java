package android.distributed.ezbluetooth.sockethandler;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.distributed.ezbluetooth.EZBluetoothService;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

public class Accept implements Runnable {
    public static final String LOG_TAG = Accept.class.getSimpleName();
    private final EZBluetoothService service;
    private final BluetoothServerSocket serverSocket;
    private final UUID uuid;

    public Accept(@NonNull EZBluetoothService service,
                  @NonNull UUID uuid) {
        this.service = service;
        this.uuid = uuid;
        // Use a temporary object that is later assigned to serverSocket,
        // because serverSocket is final
        BluetoothServerSocket tmp = null;
        try {
            // app's UUID string, also used by the client code
            tmp = service.getBluetoothAdapter().listenUsingInsecureRfcommWithServiceRecord("PROJET", uuid);
            Log.i(LOG_TAG, "started Server thread with uuid " + uuid);
        } catch (IOException e) {
            Log.e(LOG_TAG, "error when starting Server thread with uuid" + uuid, e);
        }
        serverSocket = tmp;
    }

    public void run() {
        boolean stop = false;
        while (!stop) {
            BluetoothSocket socket = null;
            try {
                socket = serverSocket.accept();
                if (socket != null) {
                    boolean success = service.registerSocket(socket);
                    if (!success) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "closing socket", e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "error during accept", e);
                stop = true;
            }
        }
        cancel();
    }

    /**
     * Will cancel the listening socket, and cause the thread to finish
     */
    public void cancel() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "closing socket", e);
        }
    }
}
