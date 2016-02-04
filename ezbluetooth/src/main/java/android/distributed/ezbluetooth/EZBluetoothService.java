package android.distributed.ezbluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.distributed.ezbluetooth.routing.BluetoothRoutingTable;
import android.distributed.ezbluetooth.routing.RoutingAlgo;
import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.bluetooth.Discoverable;
import android.distributed.ezbluetooth.routing.message.Hello;
import android.distributed.ezbluetooth.routing.message.RoutingMessage;
import android.distributed.ezbluetooth.sockethandler.Accept;
import android.distributed.ezbluetooth.sockethandler.Client;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    public static final String ACTION_RECV = "android.distributed.ezbluetooth.action.recv";
    public static final String EXTRA_RECV_MSG = "android.distributed.ezbluetooth.extra.recv_msg";
    public static final String EXTRA_RECV_SOURCE = "android.distributed.ezbluetooth.extra.recv_source";
    public static final String ACTION_NEW_CONNECTION = "android.distributed.ezbluetooth.action.new_connection";
    public static final String EXTRA_NEW_CONNECTION_ADDRESS = "android.distributed.ezbluetooth.extra.new_connection_address";
    public static final String ACTION_DISCONNECTED = "android.distributed.ezbluetooth.action.disconnected";
    public static final String EXTRA_DISCONNECTED_ADDRESS = "android.distributed.ezbluetooth.extra.disconnected_address";
    public static final String ACTION_NEW_PEER = "android.distributed.ezbluetooth.action.new_peer";
    public static final String EXTRA_NEW_PEER_ADDRESS = "android.distributed.ezbluetooth.extra.new_peer_address";
    public static final String ACTION_PEER_DISCONNECTED = "android.distributed.ezbluetooth.action.peer_disconnected";
    public static final String EXTRA_PEER_DISCONNECTED_ADDRESS = "android.distributed.ezbluetooth.extra.peer_disconnected_address";
    public static final String ACTION_NO_BLUETOOTH = "android.distributed.ezbluetooth.action.no_bluetooth";
    public static final String ACTION_BLUETOOTH_DISABLED = "android.distributed.ezbluetooth.action.bluetooth_disabled";
    public static final String ACTION_UNEXPECTED_MESSAGE = "android.distributed.ezbluetooth.action.unexpected_message_type";
    public static final String EXTRA_UNEXPECTED_MESSAGE = "android.distributed.ezbluetooth.extra.unexpected_message";
    private static final String EXTRA_UNEXPECTED_MESSAGE_SOURCE = "android.distributed.ezbluetooth.extra.unexpected_message_source";
    public static final String ACTION_ACK_RECV = "android.distributed.ezbluetooth.action.ack_recv";
    public static final String EXTRA_ACK_RECV_SEQ = "android.distributed.ezbluetooth.extra.ack_recv_seq";
    public static final UUID DEFAULT_PROTO_UUID = UUID.fromString("117adc55-ab0f-4db5-939c-0de3926a3af7");
    public static final String LOG_TAG = EZBluetoothService.class.getSimpleName();
    private static final int ID_NOTIFICATION = 0;


    //configurable options
    private int maxConn = 5;
    private boolean notificationEnabled = true;
    private boolean stopDiscovering = false;
    private boolean serverMode = true;
    private UUID protoUUID = DEFAULT_PROTO_UUID;


    private BroadcastReceiver receiver;
    private BluetoothAdapter bluetoothAdapter;
    private volatile boolean scanning;
    private ConcurrentMap<String, BluetoothSocket> connectionMapping = new ConcurrentHashMap<>();

    private RoutingAlgo routingAlgo;

    //executors
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private ExecutorService socketExecutor = ExecutorFactory.newSocketExecutor();
    private ExecutorService serverExecutor = ExecutorFactory.newServerExecutor();
    private ExecutorService connectExecutor = ExecutorFactory.newConnectExecutor();


    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            sendBroadcast(new Intent(ACTION_NO_BLUETOOTH));
            return;
        } else if (!bluetoothAdapter.isEnabled()) {
            sendBroadcast(new Intent(ACTION_BLUETOOTH_DISABLED));
        }



        executor.scheduleWithFixedDelay(this::createNotification,
                0,
                500,
                TimeUnit.MILLISECONDS);

        receiver = new BluetoothBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Register the BroadcastReceiver
        registerReceiver(receiver, filter);

        if (bluetoothAdapter.isEnabled()) {
            restartBluetoothTasks();
        }
        sendBroadcast(new Intent(ACTION_SERVICE_STARTED));
    }

    private void startDiscovery() {
        if (bluetoothAdapter.isEnabled() &&
                !stopDiscovering &&
                !scanning &&
                connectionMapping.size() < maxConn) {
            bluetoothAdapter.startDiscovery();
        }
    }

    @Nullable
    private Notification createNotification() {
        if (!notificationEnabled) {
            return null;
        }

        String contentText;
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            contentText = "Bluetooth disabled ...";
        } else if (routingAlgo != null) {
            int size = routingAlgo.getRoutingTable().getSize();
            contentText = size > 0
                    ? String.format("%d peer%s", size, size > 1 ? "s" : "")
                    : "No connection";
        } else {
            contentText = "Starting ...";
        }

        if (!connectionMapping.isEmpty()) {
            int size = connectionMapping.size();
            contentText += " / " + String.format("%d connection%s", size, size > 1 ? "s" : "");
        }

        // create notification
        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("EZBluetooth running")
                .setContentText(contentText)
                .build();

        //push it
        notificationManager().notify(ID_NOTIFICATION, notification);
        return notification;
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

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return new Binder();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        notificationManager().cancel(ID_NOTIFICATION);
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
        executor.shutdownNow();
        connectExecutor.shutdownNow();
        socketExecutor.shutdownNow();
        serverExecutor.shutdownNow();
        rmAllConnexions();
        if (routingAlgo != null) {
            routingAlgo.stop();
        }
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private boolean isConnectedTo(@NonNull String mac) {
        return connectionMapping.containsKey(mac);
    }

    private synchronized boolean addConnection(@NonNull BluetoothSocket socket) {
        String address = socket.getRemoteDevice().getAddress();
        BluetoothSocket old = connectionMapping.get(address);
        int connectionSize = connectionMapping.size();
        if (old != null ||
                connectionSize >= maxConn ||
                (connectionSize > 2 && routingAlgo != null && routingAlgo.hasRoute(address))) {
            return false;
        }
        connectionMapping.put(address, socket);
        return true;
    }

    private synchronized boolean rmConnection(@NonNull BluetoothSocket socket) {
        String address = socket.getRemoteDevice().getAddress();
        boolean removed = connectionMapping.remove(address, socket);
        if (!removed) {
            return false;
        }
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "closing socket", e);
        }
        if (routingAlgo != null){
            routingAlgo.removeRoute(address);
        }
        return true;
    }

    private short send(String macAddress, Serializable data) {
        if (routingAlgo == null) {
            return -2;
        }
        return routingAlgo.send(macAddress, data);
    }

    private void rmAllConnexions() {
        while (!connectionMapping.isEmpty()) {
            Iterator<Map.Entry<String, BluetoothSocket>> iterator = connectionMapping.entrySet().iterator();
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
        routingAlgo = new RoutingAlgo(bluetoothAdapter.getAddress());
        routingAlgo.setRecvListerner(new RecvListener());
        routingAlgo.setPeerListener(new PeerListener());
        routingAlgo.setAckListener(new AckListener());
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Discoverable.makeDiscoverable(this, bluetoothAdapter, DISCOVERABLE_TIMEOUT);
        }

        Log.i(LOG_TAG, "name " + bluetoothAdapter.getName());
        Log.i(LOG_TAG, "mac address " + bluetoothAdapter.getAddress());
        bluetoothAdapter.startDiscovery();
        startServer();
    }

    private synchronized void startServer() {
        if (serverMode) {
            Accept accept = new Accept(this, protoUUID);
            serverExecutor.execute(accept);
        }
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }


    public boolean registerSocket(@NonNull BluetoothSocket socket) {
        try {
            socketExecutor.execute(new SocketLogic(socket));
        } catch (RejectedExecutionException e) {
            Log.e(LOG_TAG, "socketExecutor can't handle this", e);
            return false;
        }
        return true;
    }

    private void connectTo(BluetoothDevice device) throws IOException {
        Client client = new Client(EZBluetoothService.this, device, protoUUID);
        client.run();
    }

    private class BluetoothBroadcastReceiver extends BroadcastReceiver {
        private List<Runnable> onDiscoveryFinishedQueue = new ArrayList<>();

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
                    if (!isConnectedTo(device.getAddress())) {
                        Log.d(LOG_TAG, "found a device");
                        onDiscoveryFinishedQueue.add(() -> {
                            try {
                                Log.i(LOG_TAG, "trying to connect to " + name);
                                connectTo(device);
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
                    Collections.shuffle(onDiscoveryFinishedQueue);
                    for (Runnable runnable : onDiscoveryFinishedQueue) {
                        Future<?> submit = connectExecutor.submit(runnable);
                        // cancel the task if that take too long
                        executor.schedule(() -> submit.cancel(true), 15 - 1, TimeUnit.SECONDS);
                    }
                    onDiscoveryFinishedQueue.clear();
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
                            onDiscoveryFinishedQueue.clear();
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
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        }

        public Iterable<String> listConnectedDevices() {
            return routingAlgo;
        }

        public short send(@NonNull String macAddress, Serializable data) {
            return EZBluetoothService.this.send(macAddress, data);
        }

        public void setMaxBluetoothConnection(int maxConn) {
            EZBluetoothService.this.maxConn = Math.max(maxConn, MAX_BLUETOOTH_CONN);
        }

        public boolean startDiscovery() {
            stopDiscovering = false;
            if (bluetoothAdapter != null) {
                EZBluetoothService.this.startDiscovery();
                return true;
            }
            return false;
        }

        public boolean stopDiscovery() {
            stopDiscovering = true;
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery();
                return true;
            }
            return false;
        }

        @Nullable
        public String getMacAddress() {
            if (bluetoothAdapter != null) {
                return bluetoothAdapter.getAddress();
            }
            return null;
        }

        public boolean serverRunning() {
            return serverMode && !serverExecutor.isTerminated();
        }

        public void removeNotification() {
            notificationEnabled = false;
            notificationManager().cancel(ID_NOTIFICATION);
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

        public boolean hasRoute(@NonNull String address) {
            return routingAlgo != null && routingAlgo.hasRoute(address);
        }

        @NonNull
        public BluetoothRoutingTable getRoutingTable() {
            if (routingAlgo == null || routingAlgo.getRoutingTable() == null) {
                return new BluetoothRoutingTable();
            }
            return routingAlgo.getRoutingTableCopy();
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

    private class RecvListener implements RoutingAlgo.RecvListener {

        @Override
        public void onRecv(@NonNull String from, Object obj) {
            //create intent and broadcast it
            Intent intent = new Intent(ACTION_RECV);
            intent.putExtra(EXTRA_RECV_MSG, (Serializable) obj);
            intent.putExtra(EXTRA_RECV_SOURCE, from);
            sendBroadcast(intent);
        }
    }

    private class PeerListener implements RoutingAlgo.PeerListener {

        @Override
        public void onPeerAdded(@NonNull String address) {
            //create intent and broadcast it
            Intent intent = new Intent(ACTION_NEW_PEER);
            intent.putExtra(EXTRA_NEW_PEER_ADDRESS, address);
            sendBroadcast(intent);
        }

        @Override
        public void onPeerRemoved(@NonNull String address) {
            //create intent and broadcast it
            Intent intent = new Intent(ACTION_PEER_DISCONNECTED);
            intent.putExtra(EXTRA_PEER_DISCONNECTED_ADDRESS, address);
            sendBroadcast(intent);
        }
    }

    private class AckListener implements RoutingAlgo.AckListener {

        @Override
        public void onAckRecv(short seq) {
            Intent intent = new Intent(ACTION_ACK_RECV);
            intent.putExtra(EXTRA_ACK_RECV_SEQ, seq);
            sendBroadcast(intent);
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
                bluetoothAdapter.cancelDiscovery();
                socketW.send(new Hello());
                connected = true;
                intent = new Intent(ACTION_NEW_CONNECTION);
                intent.putExtra(EXTRA_NEW_CONNECTION_ADDRESS, socketW.getRemoteMac());
                sendBroadcast(intent);
                while (connected) {
                    try {
                        ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                        Object o = inStream.readObject();
                        if (o instanceof RoutingMessage) {
                            Log.d(LOG_TAG, "recv " + o);
                            try{
                                ((RoutingMessage) o).accept(routingAlgo, socketW);
                            }catch (Exception e) {
                                Log.e(LOG_TAG, "visitor", e);
                            }
                        } else {
                            //unexpected message
                            intent = new Intent(ACTION_UNEXPECTED_MESSAGE);
                            intent.putExtra(EXTRA_UNEXPECTED_MESSAGE, (Serializable) o);
                            intent.putExtra(EXTRA_UNEXPECTED_MESSAGE_SOURCE, socketW.getRemoteMac());
                            sendBroadcast(intent);
                        }
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "", e);
                        break;
                    } catch (ClassNotFoundException e) {
                        Log.e(LOG_TAG, "", e);
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "", e);
            } finally {
                rmConnection(socket);
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "", e);
                }
                if (connected) {
                    intent = new Intent(ACTION_DISCONNECTED);
                    intent.putExtra(EXTRA_DISCONNECTED_ADDRESS, socketW.getRemoteMac());
                    sendBroadcast(intent);
                }
                connected = false;
            }
        }
    }
}
