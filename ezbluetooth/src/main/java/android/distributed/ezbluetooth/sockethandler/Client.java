package android.distributed.ezbluetooth.sockethandler;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.distributed.ezbluetooth.EZBluetoothService;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

public class Client implements Runnable {
    private final EZBluetoothService service;
    private final BluetoothDevice device;
    private final UUID uuid;
    private final BluetoothSocket socket;

    public Client(@NonNull EZBluetoothService service,
                  @NonNull BluetoothDevice device,
                  @NonNull UUID uuid) throws IOException {
        this.service = service;
        this.device = device;
        this.uuid = uuid;
        socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
    }

    @Override
    public void run() {
        service.getBluetoothAdapter().cancelDiscovery();
        try {
            socket.connect();
        } catch (IOException e) {
            Log.e(EZBluetoothService.LOG_TAG, "", e);
        }
        boolean success = service.registerSocket(socket);
        if (!success) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(Client.class.getSimpleName(), "closing socket", e);
            }
        }
    }
}
