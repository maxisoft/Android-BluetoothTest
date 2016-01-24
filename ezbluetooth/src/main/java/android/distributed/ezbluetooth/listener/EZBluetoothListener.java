package android.distributed.ezbluetooth.listener;

import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Created by duboi on 23/01/2016.
 */
public interface EZBluetoothListener {
    void onRecv(@NonNull String address, Serializable data);
    void onNewPeer(@NonNull String address);
    void onPeerDisconnected(@NonNull String address);
}
