package android.distributed.testbluetooth.bluetooth;


import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class UuidsWithSdp {
    public static final boolean OLD_API = Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
    public static final String ACTION_UUID = OLD_API ? "android.bleutooth.device.action.UUID" : BluetoothDevice.ACTION_UUID;
    public static final String EXTRA_DEVICE = OLD_API ? "android.bluetooth.device.extra.DEVICE" : BluetoothDevice.EXTRA_DEVICE;

    private final BluetoothDevice wrapped;

    public UuidsWithSdp(@NonNull BluetoothDevice bluetoothDevice) {
        wrapped = bluetoothDevice;
    }

    public boolean fetchUuidsWithSdp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            return wrapped.fetchUuidsWithSdp();
        } else {
            try {
                Method method = wrapped.getClass().getMethod("fetchUuidsWithSdp");
                return (boolean) method.invoke(wrapped);
            } catch (NoSuchMethodException e) {
                return false;
            } catch (InvocationTargetException e) {
                return false;
            } catch (IllegalAccessException e) {
                return false;
            }
        }
    }

    public @Nullable ParcelUuid[] getUuids() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            return wrapped.getUuids();
        } else {
            try {
                Method method = wrapped.getClass().getMethod("getUuids");
                return (ParcelUuid[]) method.invoke(wrapped);
            } catch (IllegalAccessException e) {
                return null;
            } catch (InvocationTargetException e) {
                return null;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

}
