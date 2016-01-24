package android.distributed.ezbluetooth.listener;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.distributed.ezbluetooth.EZBluetoothService;
import android.support.annotation.NonNull;

import java.io.Serializable;

public class RegisterListener {
    private RegisterListener() {
    }

    private static BroadcastReceiver receiver;

    public static void register(@NonNull Context context, EZBluetoothListener listener) {
        if (receiver != null) {
            unregister(context);
        }
        receiver = new EZBluetoothBroadcastReceiver(listener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(EZBluetoothService.ACTION_RECV);
        filter.addAction(EZBluetoothService.ACTION_NEW_PEER);
        filter.addAction(EZBluetoothService.ACTION_PEER_DISCONNECTED);
        context.registerReceiver(receiver, filter);
    }

    public static void unregister(@NonNull Context context) {
        context.unregisterReceiver(receiver);
    }

    static class EZBluetoothBroadcastReceiver extends BroadcastReceiver {
        private final EZBluetoothListener listener;

        EZBluetoothBroadcastReceiver(EZBluetoothListener listener) {
            this.listener = listener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String address;
            switch (intent.getAction()) {
                case EZBluetoothService.ACTION_RECV:
                    address = intent.getStringExtra(EZBluetoothService.EXTRA_RECV_SOURCE);
                    Serializable data = intent.getSerializableExtra(EZBluetoothService.EXTRA_RECV_MSG);
                    listener.onRecv(address, data);
                    break;
                case EZBluetoothService.ACTION_NEW_PEER:
                    address = intent.getStringExtra(EZBluetoothService.EXTRA_NEW_PEER_ADDRESS);
                    listener.onNewPeer(address);
                    break;
                case EZBluetoothService.ACTION_PEER_DISCONNECTED:
                    address = intent.getStringExtra(EZBluetoothService.EXTRA_PEER_DISCONNECTED_ADDRESS);
                    listener.onPeerDisconnected(address);
                    break;
            }
        }
    }
}
