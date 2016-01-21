package android.distributed.ezbluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.distributed.ezbluetooth.routing.RoutingAlgo;
import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.bluetooth.Discoverable;
import android.distributed.ezbluetooth.routing.bluetooth.ServiceUuidGenerator;
import android.distributed.ezbluetooth.routing.message.Hello;
import android.distributed.ezbluetooth.routing.message.RoutingMessage;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class EZBluetoothService extends Service {
    public static final int DISCOVERABLE_TIMEOUT = 0;
    public static final int MAX_BLUETOOTH_CONN = 7;
    public static final String ACTION_SERVICE_STARTED = "android.distributed.ezbluetooth.action.service_started";
    public static final String ACTION_NEW_CONNECTION = "android.distributed.ezbluetooth.action.new_connection";
    public static final String EXTRA_NEW_CONNECTION_ADDRESS = "android.distributed.ezbluetooth.extra.new_connection_address";
    public static final String ACTION_DISCONNECTED = "android.distributed.ezbluetooth.action.disconnected";
    public static final String EXTRA_DISCONNECTED_ADDRESS = "android.distributed.ezbluetooth.extra.disconnected_address";
    public static final String ACTION_NO_BLUETOOTH = "android.distributed.ezbluetooth.action.no_bluetooth";
    public static final String ACTION_BLUETOOTH_DISABLED = "android.distributed.ezbluetooth.action.bluetooth_disabled";
    public static final String ACTION_UNEXPECTED_MESSAGE = "android.distributed.ezbluetooth.action.unexpected_message_type";
    public static final String EXTRA_UNEXPECTED_MESSAGE = "android.distributed.ezbluetooth.extra.unexpected_message";
    public static final UUID DEFAULT_PROTO_UUID = UUID.fromString("117adc55-ab0f-4db5-939c-0de3926a3af7");
    public static final String LOG_TAG = EZBluetoothService.class.getSimpleName();
    private static final int ID_NOTIFICATION = 0;
    //executors
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private int maxConn = 4;
    private boolean notificationEnabled = true;
    private boolean stopDiscovering = false;
    private boolean serverMode = true;
    private UUID protoUUID = DEFAULT_PROTO_UUID;
    private BroadcastReceiver mReceiver;
    private BluetoothAdapter mBluetoothAdapter;
    private volatile boolean scanning;
    private ConcurrentMap<String, BluetoothSocket> connexionMapping = new ConcurrentHashMap<>();
    private RoutingAlgo routingAlgo;
    private ExecutorService socketExecutor = ExecutorFactory.newSocketExecutor();
    private ExecutorService serverExecutor = ExecutorFactory.newServerExecutor();
    private ExecutorService connectExecutor = ExecutorFactory.newConnectExecutor();


    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            sendBroadcast(new Intent(ACTION_NO_BLUETOOTH));
            return;
        } else if (!mBluetoothAdapter.isEnabled()) {
            sendBroadcast(new Intent(ACTION_BLUETOOTH_DISABLED));
        }

        executor.scheduleAtFixedRate(() -> {
                    if (mBluetoothAdapter.isEnabled() &&
                            !stopDiscovering &&
                            !scanning &&
                            connexionMapping.size() < maxConn) {
                        mBluetoothAdapter.startDiscovery();
                    }
                },
                500,
                TimeUnit.SECONDS.toMillis(15),
                TimeUnit.MILLISECONDS);


        if (notificationEnabled) {
            // create notification
            Notification notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle("EZBluetooth running")
                    .setContentText("...")
                    .build();

            //push it
            notificationManager().notify(ID_NOTIFICATION, notification);
        }
        mReceiver = new BluetoothBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Register the BroadcastReceiver
        registerReceiver(mReceiver, filter);

        if (mBluetoothAdapter.isEnabled()) {
            restartBluetoothTasks();
        }
        sendBroadcast(new Intent(ACTION_SERVICE_STARTED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return Service.START_NOT_STICKY;
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return new Binder();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        notificationManager().cancel(ID_NOTIFICATION);
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }
        executor.shutdownNow();
        connectExecutor.shutdownNow();
        socketExecutor.shutdownNow();
        serverExecutor.shutdownNow();
        rmAllConnexions();
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private boolean isConnectedTo(@NonNull String mac) {
        return connexionMapping.containsKey(mac);
    }

    private boolean addConnection(@NonNull BluetoothSocket socket) {
        BluetoothSocket old = connexionMapping.get(socket.getRemoteDevice().getAddress());
        if (old != null) {
            return false;
        }
        connexionMapping.put(socket.getRemoteDevice().getAddress(), socket);
        return true;
    }

    private boolean rmConnection(@NonNull BluetoothSocket socket) {
        String address = socket.getRemoteDevice().getAddress();
        boolean removed = connexionMapping.remove(address, socket);
        if (!removed) {
            return false;
        }
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "closing socket", e);
        }
        routingAlgo.removeRoute(address);

        //TODO notify
        return true;
    }

    private void rmAllConnexions() {
        while (!connexionMapping.entrySet().isEmpty()) {
            Iterator<Map.Entry<String, BluetoothSocket>> iterator = connexionMapping.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<String, BluetoothSocket> entry = iterator.next();
                rmConnection(entry.getValue());
            }
        }
    }

    private void restartBluetoothTasks() {
        if (routingAlgo != null) {
            routingAlgo.stop();
        }
        routingAlgo = new RoutingAlgo(mBluetoothAdapter.getAddress());
        mBluetoothAdapter.setName(String.format("%s-%s", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), protoUUID));
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Discoverable.makeDiscoverable(this, mBluetoothAdapter, DISCOVERABLE_TIMEOUT);
        }

        Log.i(LOG_TAG, "name " + mBluetoothAdapter.getName());
        Log.i(LOG_TAG, "mac address " + mBluetoothAdapter.getAddress());
        mBluetoothAdapter.startDiscovery();
        startServer();
    }

    private synchronized void startServer() {
        if (serverMode) {
            UUID uuid = new ServiceUuidGenerator(protoUUID).generate(mBluetoothAdapter.getAddress());
            AcceptThread acceptThread = new AcceptThread(uuid);
            serverExecutor.execute(acceptThread);
        }
    }

    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private Queue<Runnable> onDiscoveryFinishQueue = new ArrayDeque<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    // Get the BluetoothDevice object from the Intent
                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(LOG_TAG, "Found Device " + device.getName());
                    String name = device.getName();
                    if (name != null && !isConnectedTo(device.getAddress())) {
                        Log.d(LOG_TAG, "found a device");
                        onDiscoveryFinishQueue.add(() -> {
                            try {
                                Log.i(LOG_TAG, "trying to connect to " + name);
                                UUID uuid = new ServiceUuidGenerator(protoUUID).generate(device.getAddress());
                                Client client = new Client(device, uuid);
                                Log.i(LOG_TAG, "using " + client);
                                executor.execute(client);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "when connecting to " + name, e);
                            }
                        });

                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    scanning = true;
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    scanning = false;
                    //poll the onDiscoveryFinish queue
                    Runnable runnable = onDiscoveryFinishQueue.poll();
                    while (runnable != null) {
                        Log.d(LOG_TAG, "got a runnable");
                        Future<?> submit = connectExecutor.submit(runnable);
                        // cancel the task if that take too long
                        executor.schedule(() -> submit.cancel(true), 15 - 1, TimeUnit.SECONDS);
                        //pick up next runnable (if any)
                        runnable = onDiscoveryFinishQueue.poll();
                    }
                    break;
                case BluetoothAdapter.ACTION_SCAN_MODE_CHANGED:  //When Bluetooth adapter scan mode change
                    final int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                    final int oldScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, -1);
                    switch (scanMode) {
                        case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                            Log.i(LOG_TAG, "Device is now DISCOVERABLE");
                            break;
                        case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                            Log.i(LOG_TAG, "Device is now CONNECTABLE");
                            break;
                        case BluetoothAdapter.SCAN_MODE_NONE:
                            Log.i(LOG_TAG, "Device isn't CONNECTABLE");
                            break;
                    }
                    break;
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    switch (state) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            onDiscoveryFinishQueue.clear();
                            routingAlgo.stop();
                            socketExecutor.shutdownNow();
                            serverExecutor.shutdownNow();
                            connectExecutor.shutdownNow();
                            rmAllConnexions();
                            break;
                        case BluetoothAdapter.STATE_OFF:
                            scanning = false;
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            break;
                        case BluetoothAdapter.STATE_ON:
                            //restart all executors if needed
                            if (socketExecutor.isShutdown()) {
                                socketExecutor = ExecutorFactory.newSocketExecutor();
                            }
                            if (serverExecutor.isShutdown()) {
                                serverExecutor = ExecutorFactory.newServerExecutor();
                            }
                            if (connectExecutor.isShutdown()) {
                                connectExecutor = ExecutorFactory.newConnectExecutor();
                            }
                            restartBluetoothTasks();
                            break;
                    }

                    break;
            }
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class Binder extends android.os.Binder {

        public boolean isBluetoothEnabled() {
            return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
        }

        public void removeNotification() {
            notificationEnabled = false;
            notificationManager().cancel(ID_NOTIFICATION);
        }

        public Iterable<String> listConnectedDevices() {
            return routingAlgo;
        }

        public boolean send(String macAddress, Serializable data) throws IOException {
            return routingAlgo.send(macAddress, data);
        }

        public void setMaxBluetoothConnection(int maxConn) {
            EZBluetoothService.this.maxConn = Math.max(maxConn, MAX_BLUETOOTH_CONN);
        }

        public boolean startDiscovery() {
            stopDiscovering = false;
            if (mBluetoothAdapter != null) {
                mBluetoothAdapter.startDiscovery();
                return true;
            }
            return false;
        }

        public boolean stopDiscovery() {
            stopDiscovering = true;
            if (mBluetoothAdapter != null) {
                mBluetoothAdapter.cancelDiscovery();
                return true;
            }
            return false;
        }

        @Nullable
        public String getMacAddress() {
            if (mBluetoothAdapter != null) {
                return mBluetoothAdapter.getAddress();
            }
            return null;
        }

        public boolean serverRunning() {
            return serverMode && !serverExecutor.isTerminated();
        }

        public boolean stopServer() {
            if (serverMode) {
                serverExecutor.shutdownNow();
                serverMode = false;
                return true;
            }
            return false;
        }

        public boolean startServer() {
            if (!serverMode) {
                EZBluetoothService.this.startServer();
                serverMode = true;
            }
            return false;
        }

        /**
         * Change the base uuid used to connect devices together.
         * <br/> This will reset any connection and the routing table content
         *
         * @param uuid
         */
        public void setProtoUUID(@NonNull UUID uuid) {
            protoUUID = uuid;
            restartBluetoothTasks();
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;
        private final UUID uuid;

        public AcceptThread(@NonNull UUID uuid) {
            this.uuid = uuid;
            // Use a temporary object that is later assigned to serverSocket,
            // because serverSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("PROJET", uuid);
                Log.i(LOG_TAG, "started Server thread with uuid " + uuid);
            } catch (IOException e) {
                Log.e(LOG_TAG, "error when starting Server thread with uuid" + uuid, e);
            }
            serverSocket = tmp;
        }

        public void run() {
            while (true) {
                final BluetoothSocket socket;
                try {
                    socket = serverSocket.accept();
                    if (socket != null) {
                        socketExecutor.execute(new SocketLogic(socket));
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "error during accept", e);
                    break;
                } catch (RejectedExecutionException e) {
                    Log.e(LOG_TAG, "socketExecutor can't handle this", e);
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
            }
        }
    }

    private class Client implements Runnable {
        private final BluetoothDevice device;
        private final UUID uuid;
        private final BluetoothSocket socket;

        public Client(BluetoothDevice device, UUID uuid) throws IOException {
            this.device = device;
            this.uuid = uuid;
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        }

        @Override
        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                socket.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                socketExecutor.execute(new SocketLogic(socket));
            } catch (RejectedExecutionException e) {
                Log.e(LOG_TAG, "socketExecutor can't handle this", e);
            }
        }
    }

    private class SocketLogic implements Runnable {
        private final BluetoothSocket socket;
        private boolean connected;

        private SocketLogic(@NonNull BluetoothSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            SocketWrapper socketW = new SocketWrapper(socket);
            Intent intent;
            try {
                if (!addConnection(socket)) {
                    return;
                }
                connected = true;
                mBluetoothAdapter.cancelDiscovery();
                socketW.send(new Hello());

                intent = new Intent(ACTION_NEW_CONNECTION);
                intent.putExtra(EXTRA_NEW_CONNECTION_ADDRESS, socketW.getRemoteMac());
                sendBroadcast(intent);
                while (connected) {
                    try {
                        ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                        Object o = inStream.readObject();
                        if (o instanceof RoutingMessage) {
                            Log.d(LOG_TAG, "recv " + o);
                            ((RoutingMessage) o).accept(routingAlgo, socketW);
                            //snakeBar("table :" + devices);
                        } else {
                            //unexpected message
                            intent = new Intent(ACTION_UNEXPECTED_MESSAGE);
                            intent.putExtra(EXTRA_UNEXPECTED_MESSAGE, (Serializable) o);
                            sendBroadcast(intent);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        connected = false;
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "", e);
            } finally {
                rmConnection(socket);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (connected) { //was connected
                    intent = new Intent(ACTION_DISCONNECTED);
                    intent.putExtra(EXTRA_DISCONNECTED_ADDRESS, socketW.getRemoteMac());
                    sendBroadcast(intent);
                }
                connected = false;
            }
        }
    }
}
