package android.distributed.ezbluetooth;


import android.support.annotation.NonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class ExecutorFactory {
    private ExecutorFactory() {
    }

    @NonNull
    public static ThreadPoolExecutor newSocketExecutor() {
        return new ThreadPoolExecutor(0, EZBluetoothService.MAX_BLUETOOTH_CONN,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1));
    }

    @NonNull
    public static ExecutorService newServerExecutor() {
        return Executors.newSingleThreadExecutor();
    }


    @NonNull
    public static ExecutorService newConnectExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
