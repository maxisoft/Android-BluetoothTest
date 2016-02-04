package android.distributed.ezbluetooth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BlockingApi implements Closeable {
    private final EZBluetoothService.Binder binder;
    private final Context context;
    private final ReentrantLock lock;
    private final SparseArray<Condition> sendConditions;
    private final BroadcastReceiver receiver;
    private final BlockingQueue<RecvMessage> recvQueue;

    public BlockingApi(Context context, EZBluetoothService.Binder binder) {
        this(context, binder, 50);
    }

    public BlockingApi(Context context, EZBluetoothService.Binder binder, int recvQueueSize) {
        this.binder = binder;
        this.context = context;
        lock = new ReentrantLock();
        sendConditions = new SparseArray<Condition>();
        receiver = new BlockingApiBroadcastReceiver();
        recvQueue = new ArrayBlockingQueue<>(recvQueueSize, true);
        IntentFilter filter = new IntentFilter();
        filter.addAction(EZBluetoothService.ACTION_RECV);
        filter.addAction(EZBluetoothService.ACTION_ACK_RECV);
        context.registerReceiver(receiver, filter);
    }

    public boolean send(String address, Serializable data) throws InterruptedException {
        return send(address, data, 15, TimeUnit.SECONDS);
    }

    public boolean send(String address, Serializable data, long timeout, TimeUnit unit) throws InterruptedException {
        lock.lock();
        try{
            short id = binder.send(address, data);
            return await(id, timeout, unit);
        }finally {
            lock.unlock();
        }
    }

    private boolean await(short id, long timeout, TimeUnit timeUnit) throws InterruptedException {
        lock.lock();
        try{
            Condition condition = sendConditions.get(id, null);
            if (condition == null) {
                condition = lock.newCondition();
                sendConditions.put(id, condition);
            }
            return condition.await(timeout, timeUnit);
        }finally {
            sendConditions.remove(id);
            lock.unlock();
        }
    }

    private boolean signal(short id){
        lock.lock();
        try{
            Condition condition = sendConditions.get(id, null);
            if (condition != null) {
                condition.signal();
                return true;
            }
        }finally {
            lock.unlock();
        }
        return false;
    }

    public RecvMessage recv(long timeout, TimeUnit unit) throws InterruptedException {
        return recvQueue.poll(timeout, unit);
    }

    public RecvMessage recv() throws InterruptedException {
        return recv(15 , TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        context.unregisterReceiver(receiver);
    }

    public static class RecvMessage {
        private final Serializable data;
        private final String source;
        private final Date date;

        RecvMessage(Serializable data, String source) {
            this.date = new Date();
            this.data = data;
            this.source = source;
        }

        public Serializable getData() {
            return data;
        }

        public String getSource() {
            return source;
        }

        public Date getDate() {
            return date;
        }
    }

    private class BlockingApiBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String address;
            switch (intent.getAction()) {
                case EZBluetoothService.ACTION_RECV:
                    address = intent.getStringExtra(EZBluetoothService.EXTRA_RECV_SOURCE);
                    Serializable data = intent.getSerializableExtra(EZBluetoothService.EXTRA_RECV_MSG);
                    try{
                        recvQueue.add(new RecvMessage(data, address));
                    }catch (IllegalStateException e){
                        Log.e(BlockingApi.class.getSimpleName(), "recv Queue is full", e);
                    }
                    break;
                case EZBluetoothService.ACTION_ACK_RECV:
                    short seq = intent.getShortExtra(EZBluetoothService.EXTRA_ACK_RECV_SEQ, (short) -1);
                    if (seq != -1) {
                        try{
                            signal(seq);
                        }catch (Exception e) {
                            Log.e(BlockingApi.class.getSimpleName(), "unexpeted exception using signal.", e);
                        }
                    }
                    break;
            }
        }
    }
}

