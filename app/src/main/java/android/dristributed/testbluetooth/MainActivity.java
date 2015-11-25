package android.dristributed.testbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.dristributed.testbluetooth.bluetooth.Discoverable;
import android.dristributed.testbluetooth.bluetooth.ServiceUuidGenerator;
import android.dristributed.testbluetooth.bluetooth.UuidsWithSdp;
import android.dristributed.testbluetooth.message.AlreadyConnected;
import android.dristributed.testbluetooth.message.Full;
import android.dristributed.testbluetooth.message.Message;
import android.dristributed.testbluetooth.message.SwitchSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT = 0xB1;
    public static final int DISCOVERABLE_TIMEOUT = 0;
    public static final UUID PROTO_UUID = UUID.fromString("117adc55-ab0f-4db5-939c-0de3926a3af7");
    public static boolean ENABLE_SERVER_MODE = true;
    public static boolean HARDCODED_NETWORK_SETUP = true;
    public static int MAX_CLIENT = 3;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    private final Map<BluetoothDevice, RoutingServerThread> clients = Collections.synchronizedMap(new ArrayMap<>());
    private final Map<BluetoothDevice, ConnectThread> connectThreadMapping = new ArrayMap<>();
    private final Map<BluetoothDevice, RoutingClientThread> routingClientMapping = new ArrayMap<>();
    private final Queue<Runnable> onDiscoveryFinishQueue = new ConcurrentLinkedQueue<>();
    BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mReceiver;
    private volatile boolean scanning;
    private Runnable startDiscoveryRunnable;
    private AtomicInteger clientsCount = new AtomicInteger(0); //TODO replace all use with clients.size()
    private ScheduledFuture hardCodedNetworkFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> mBluetoothAdapter.startDiscovery());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        startDiscoveryRunnable = mBluetoothAdapter::startDiscovery;

        mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                    // Get the BluetoothDevice object from the Intent
                    final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    //snakeBar("Found Device " + device.getName());
                    synchronized (connectThreadMapping) {
                        String name = device.getName();
                        if (name != null && name.endsWith(PROTO_UUID.toString()) &&
                                !connectThreadMapping.containsKey(device) &&
                                !routingClientMapping.containsKey(device)) {
                            Log.i("bluetooth", "found a device");
                            onDiscoveryFinishQueue.add(() -> {
                                try {
                                    mBluetoothAdapter.cancelDiscovery();
                                    Log.i("bluetooth", "trying to connect to " + name);
                                    ConnectThread connectThread = new ConnectThread(device, PROTO_UUID);
                                    Log.i("bluetooth", "using " + connectThread);
                                    connectThreadMapping.put(device, connectThread);
                                    executor.execute(connectThread);
                                    snakeBar("Found matching service on " + name);
                                } catch (Exception e) {
                                    Log.e("bluetooth", "when connecting to " + name, e);
                                }
                            });
                        }
                    }
                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                    scanning = true;
                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                    scanning = false;
                    Runnable runnable;
                    do {
                        runnable = onDiscoveryFinishQueue.poll();
                        if (runnable != null) {
                            Log.i("bluetooth", "got a runable");
                            executor.submit(runnable);
                        }
                    }
                    while (runnable != null);

                    handler.removeCallbacks(startDiscoveryRunnable);
                    handler.postDelayed(startDiscoveryRunnable, 60_000);
                } else if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) { //When Bluetooth adapter scan mode change
                    final int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                    final int oldScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, -1);
                    switch (scanMode) {
                        case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                            Log.i("bluetooth", "Device is now DISCOVERABLE");
                            break;
                        case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                            Log.i("bluetooth", "Device is now CONNECTABLE");
                            break;
                        case BluetoothAdapter.SCAN_MODE_NONE:
                            Log.i("bluetooth", "Device isn't CONNECTABLE");
                            break;
                    }

                } else if (action.equals(UuidsWithSdp.ACTION_UUID)) { // No more use// When a device's uuid lookup ended

                }

            }
        };


        if (HARDCODED_NETWORK_SETUP) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                        synchronized (this) {
                            if (hardCodedNetworkFuture == null) {
                                hardCodedNetworkFuture = executor.scheduleWithFixedDelay(MainActivity.this::hardcodedNetworkSetup, 1, 10, TimeUnit.SECONDS);
                            }
                        }
                    }
                }
            };
            registerReceiver(mReceiver, filter);
            mBluetoothAdapter.startDiscovery();
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
            filter.addAction(UuidsWithSdp.ACTION_UUID);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            // Register the BroadcastReceiver
            registerReceiver(mReceiver, filter);

            mBluetoothAdapter.startDiscovery();
            mBluetoothAdapter.setName(String.format("%s-%s", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), PROTO_UUID));
            if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                Discoverable.makeDiscoverable(MainActivity.this, mBluetoothAdapter, DISCOVERABLE_TIMEOUT);
            }
        }

        Log.i("bluetooth", "name " + mBluetoothAdapter.getName());
        Log.i("bluetooth", "mac address " + mBluetoothAdapter.getAddress());
        snakeBar(mBluetoothAdapter.getAddress());

        if (ENABLE_SERVER_MODE) {
            AcceptThread acceptThread = new AcceptThread(PROTO_UUID);
            executor.execute(acceptThread);
        }

    }

    /**
     * Used by {@link #hardcodedNetworkSetup}
     */
    private boolean lazyConnectTo(BluetoothDevice device) {
        try {
            ConnectThread connectThread = new ConnectThread(device, PROTO_UUID);
            synchronized (connectThreadMapping) {
                if (!connectThreadMapping.containsKey(device) && !routingClientMapping.containsKey(device)) {
                    connectThreadMapping.put(device, connectThread);
                    executor.execute(connectThread);
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void hardcodedNetworkSetup() {
        final BluetoothDevice x8 = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.X8);
        final BluetoothDevice wikoSlim = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.WIKO_SLIM);
        final BluetoothDevice tabA = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.TAB_A);
        final BluetoothDevice tabB = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.TAB_B);
        final BluetoothDevice lgF60 = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.LG_F60);
        final BluetoothDevice n4 = mBluetoothAdapter.getRemoteDevice(MyPhonesMac.N4);

        switch (mBluetoothAdapter.getAddress()) {
            case MyPhonesMac.LG_F60:
                lazyConnectTo(wikoSlim);
                break;
            case MyPhonesMac.WIKO_SLIM:
                lazyConnectTo(tabA);
                break;
            case MyPhonesMac.TAB_A:
                lazyConnectTo(tabB);
                break;
            case MyPhonesMac.TAB_B:
                lazyConnectTo(n4);
            case MyPhonesMac.N4:
                lazyConnectTo(lgF60);
            default:
                Log.w("bluetooth", "unexpected device " + mBluetoothAdapter.getAddress());
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onPause() {
        mBluetoothAdapter.cancelDiscovery();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception ignored) {
        }

        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == R.id.action_reset) {
            synchronized (connectThreadMapping) {
                for (Map.Entry<BluetoothDevice, ConnectThread> entry : connectThreadMapping.entrySet()) {
                    entry.getValue().cancel();
                }
                connectThreadMapping.clear();
            }
            synchronized (clients) {
                for (Map.Entry<BluetoothDevice, RoutingServerThread> entry : clients.entrySet()) {
                    entry.getValue().cancel();
                }
                clients.clear();
            }
            synchronized (routingClientMapping) {
                for (Map.Entry<BluetoothDevice, RoutingClientThread> entry : routingClientMapping.entrySet()) {
                    entry.getValue().cancel();
                }
                routingClientMapping.clear();

            }
            clientsCount.set(0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void snakeBar(String text) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private final UUID uuid;

        public AcceptThread(@NonNull UUID uuid) {
            this.uuid = uuid;
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("PROJET", uuid);
                Log.i("bluetooth", "started Server thread with uuid " + uuid);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            try {
                //while (true) {
                try {
                    final BluetoothSocket socket = mmServerSocket.accept();
                    if (socket != null) {
                        BluetoothDevice remoteDevice = socket.getRemoteDevice();
                        executor.execute(() -> {
                            try {
                                Future scheduled = null;
                                mBluetoothAdapter.cancelDiscovery();
                                try {
                                    Message message;
                                    if (clientsCount.get() >= MAX_CLIENT) {
                                        Log.w("bluetooth", "max client connected !");
                                        message = new Full();
                                    } else if (clients.containsKey(remoteDevice) || routingClientMapping.containsKey(remoteDevice)) {
                                        Log.w("bluetooth", "there is already a socket to " + remoteDevice);
                                        message = clients.containsKey(remoteDevice) ?
                                                new AlreadyConnected(mBluetoothAdapter.getAddress(), remoteDevice.getAddress()) :
                                                new AlreadyConnected(remoteDevice.getAddress(), mBluetoothAdapter.getAddress());
                                    } else {
                                        UUID newUuid = new ServiceUuidGenerator(uuid).generate(remoteDevice.getAddress());
                                        Log.i("bluetooth", "switching client to new uuid " + newUuid);
                                        message = new SwitchSocket(newUuid);
                                        RoutingServerThread task = new RoutingServerThread(newUuid);
                                        scheduled = executor.submit(task);
                                        clients.put(remoteDevice, task);
                                    }

                                    new ObjectOutputStream(socket.getOutputStream()).writeObject(message);
                                    socket.getOutputStream().flush();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    clients.remove(remoteDevice);
                                    if (scheduled != null) {
                                        scheduled.cancel(true);
                                    }
                                }
                            } finally {
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //}
            } finally {
                cancel();
                executor.schedule(new AcceptThread(uuid), 250, TimeUnit.MILLISECONDS);
            }
        }

        /**
         * Will cancel the listening socket, and cause the thread to finish
         */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class RoutingClientThread implements Runnable {
        private final BluetoothDevice device;
        private final UUID uuid;
        private final BluetoothSocket socket;

        public RoutingClientThread(BluetoothDevice device, UUID uuid) throws IOException {
            this.device = device;
            this.uuid = uuid;
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        }

        @Override
        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            boolean connected = false;
            try {
                socket.connect();
                connected = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (connected) {
                mBluetoothAdapter.cancelDiscovery();
                try {
                    socket.getOutputStream().write(("hello from " + mBluetoothAdapter.getName()).getBytes());
                    Thread.sleep(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
                } catch (IOException e) {
                    e.printStackTrace();
                    connected = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            cancel();
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            synchronized (routingClientMapping) {
                routingClientMapping.remove(device);
            }
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    private class RoutingServerThread implements Runnable {
        private final BluetoothServerSocket serverSocket;
        private final UUID uuid;

        public RoutingServerThread(@NonNull UUID uuid) throws IOException {
            this.uuid = uuid;
            serverSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("routing", uuid);
            Log.i("bluetooth", "started Server thread with uuid " + uuid);
        }

        @Override
        public void run() {
            try {
                //while (true) {
                BluetoothSocket socket = null;
                try {
                    socket = serverSocket.accept((int) TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS));
                    if (socket != null) {

                        Log.i("bluetooth", "got client" + clients.keySet());
                        clientsCount.incrementAndGet();
                        // Do work to manage the connection (in a separate thread)
                        final BluetoothSocket finalSocket = socket;
                        executor.execute(() -> {
                            try {
                                byte[] buff = new byte[256];
                                while (true) {
                                    //Arrays.fill(buff, (byte) 0);
                                    int read = 0;
                                    try {
                                        mBluetoothAdapter.cancelDiscovery();
                                        read = finalSocket.getInputStream().read(buff);
                                        if (read < 0) {
                                            snakeBar("device " + finalSocket.getRemoteDevice().getName() + " disconnected");
                                            break;
                                        }
                                        Log.i("bluetooth", "RECEIVED " + read + " bytes");
                                    } catch (IOException e) {
                                        snakeBar("device " + finalSocket.getRemoteDevice().getName() + " disconnected");
                                        e.printStackTrace();
                                        break;
                                    }
                                    Log.i("bluetooth", "RECEIVED " + new String(buff, 0, read));
                                    snakeBar(new String(buff, 0, read));
                                }

                                try {
                                    finalSocket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } finally {
                                clients.remove(finalSocket.getRemoteDevice());
                                clientsCount.decrementAndGet();
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (socket != null) {
                        clients.remove(socket.getRemoteDevice());
                    }
                }
                //}
            } finally {
                cancel();

            }

        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID uuid;

        public ConnectThread(BluetoothDevice device, UUID uuid) throws IOException {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            this.uuid = uuid;
            mmDevice = device;
            BluetoothSocket tmp;
            tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
            mmSocket = tmp;
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            boolean connected = false;
            int maxtries = 5;
            while (!connected && maxtries > 0) {
                maxtries -= 1;
                try {
                    mmSocket.connect();
                    connected = true;
                } catch (IOException e) {
                    e.printStackTrace();
                    if (e.getMessage().equalsIgnoreCase("Service discovery failed")) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
            while (connected) {
                // Cancel discovery because it will slow down the connection
                mBluetoothAdapter.cancelDiscovery();
                try {
                    Message message = (Message) new ObjectInputStream(mmSocket.getInputStream()).readObject();
                    if (message instanceof Full) {
                        //TODO temporary blacklist device
                        connected = false;
                    } else if (message instanceof SwitchSocket) {
                        UUID uuid = ((SwitchSocket) message).getUuid();
                        synchronized (routingClientMapping) {
                            if (!routingClientMapping.containsKey(mmDevice)) {
                                RoutingClientThread command = new RoutingClientThread(mmDevice, uuid);
                                routingClientMapping.put(mmDevice, command);
                                executor.schedule(command, 500, TimeUnit.MILLISECONDS);
                            }
                        }
                        connected = false;
                    } else {
                        Log.w("bluetooth", "unexpected message " + message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    connected = false;
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            cancel();
            Log.i("bluetooth", "connection to " + mmDevice.getAddress() + "closed");

        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            synchronized (connectThreadMapping) {
                connectThreadMapping.remove(mmDevice);
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
