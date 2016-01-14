package android.dristributed.testbluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.databinding.DataBindingUtil;
import android.dristributed.testbluetooth.bluetooth.Discoverable;
import android.dristributed.testbluetooth.bluetooth.ServiceUuidGenerator;
import android.dristributed.testbluetooth.bluetooth.UuidsWithSdp;
import android.dristributed.testbluetooth.databinding.MainActivityBinding;
import android.dristributed.testbluetooth.routing.RoutingAlgo;
import android.dristributed.testbluetooth.routing.SocketWrapper;
import android.dristributed.testbluetooth.routing.message.Hello;
import android.dristributed.testbluetooth.routing.message.RoutingMessage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT = 0xB1;
    public static final int DISCOVERABLE_TIMEOUT = 0;
    public static final UUID PROTO_UUID = UUID.fromString("117adc55-ab0f-4db5-939c-0de3926a3af7");
    public static boolean ENABLE_SERVER_MODE = true;
    public static int MAX_CONN = 4;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private ExecutorService socketExecutor = newSocketExecutor();
    private ExecutorService serverExecutor = newServerExecutor();
    private ExecutorService connectExecutor = newConnectExecutor();

    private final AtomicInteger connexionCount = new AtomicInteger();
    BluetoothAdapter mBluetoothAdapter;
    private Queue<Runnable> onDiscoveryFinishQueue = new ArrayDeque<>();
    private BroadcastReceiver mReceiver;
    private volatile boolean scanning;
    private Runnable startDiscoveryRunnable;
    private ConcurrentMap<String, BluetoothSocket> connexionMapping = new ConcurrentHashMap<>();
    private MainActivityBinding binding;
    private RoutingAlgo routingAlgo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        binding.setConnexion(connexionCount);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> mBluetoothAdapter.startDiscovery());
    }

    private boolean addConnexion(@NonNull BluetoothSocket socket) {
        BluetoothSocket old = connexionMapping.get(socket.getRemoteDevice().getAddress());
        if (old != null) {
            return false;
        }
        connexionMapping.put(socket.getRemoteDevice().getAddress(), socket);
        connexionCount.set(connexionMapping.size());
        binding.setConnexion(connexionCount);
        binding.setRoutingTableAsString(routingAlgo.toString());
        return true;
    }

    private boolean rmConnexion(@NonNull BluetoothSocket socket) {
        String address = socket.getRemoteDevice().getAddress();
        boolean removed = connexionMapping.remove(address, socket);
        if (!removed) {
            return false;
        }
        try {
            socket.close();
        } catch (IOException e) {
            Log.e("bluetooth", "closing socket", e);
        }
        routingAlgo.removeRoute(address);

        connexionCount.set(connexionMapping.size());
        binding.setConnexion(connexionCount);
        binding.setRoutingTableAsString(routingAlgo.toString());
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        Log.i("bluetooth", "saving InstanceState");
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.i("bluetooth", "restoring InstanceState");
    }

    private boolean isConnectedTo(@NonNull String mac) {
        return connexionMapping.containsKey(mac);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            //TODO
            return;
        } else if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        startDiscoveryRunnable = () -> {
            if (mBluetoothAdapter.isEnabled() && !scanning && connexionMapping.size() < MAX_CONN) {
                mBluetoothAdapter.startDiscovery();
            }
        };

        executor.scheduleAtFixedRate(startDiscoveryRunnable,
                500,
                TimeUnit.SECONDS.toMillis(15),
                TimeUnit.MILLISECONDS);

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
                    String name = device.getName();
                    if (name != null && !isConnectedTo(device.getAddress())) {
                        Log.d("bluetooth", "found a device");
                        onDiscoveryFinishQueue.add(() -> {
                            try {
                                Log.i("bluetooth", "trying to connect to " + name);
                                UUID uuid = new ServiceUuidGenerator(PROTO_UUID).generate(device.getAddress());
                                Client client = new Client(device, uuid);
                                Log.i("bluetooth", "using " + client);
                                executor.execute(client);
                            } catch (Exception e) {
                                Log.e("bluetooth", "when connecting to " + name, e);
                            }
                        });

                    }
                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                    scanning = true;
                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                    scanning = false;
                    //poll the onDiscoveryFinish queue
                    Runnable runnable = onDiscoveryFinishQueue.poll();
                    while (runnable != null) {
                        Log.d("bluetooth", "got a runnable");
                        Future<?> submit = connectExecutor.submit(runnable);
                        // cancel the task if that take too long
                        executor.schedule(() -> submit.cancel(true), 15 - 1, TimeUnit.SECONDS);
                        //pick up next runnable (if any)
                        runnable = onDiscoveryFinishQueue.poll();
                    }
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
                } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
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
                                socketExecutor = newSocketExecutor();
                            }
                            if (serverExecutor.isShutdown()){
                                serverExecutor = newServerExecutor();
                            }
                            if (connectExecutor.isShutdown()) {
                                connectExecutor = newConnectExecutor();
                            }
                            restartOurBluetoothServices();
                            break;
                    }

                } else if (action.equals(UuidsWithSdp.ACTION_UUID)) { // No more use// When a device's uuid lookup ended

                }

            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(UuidsWithSdp.ACTION_UUID);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // Register the BroadcastReceiver
        registerReceiver(mReceiver, filter);

        if (mBluetoothAdapter.isEnabled()) {
            restartOurBluetoothServices();
        }
    }

    private void restartOurBluetoothServices() {
        if (routingAlgo != null){
            routingAlgo.stop();
        }
        routingAlgo = new RoutingAlgo(mBluetoothAdapter.getAddress());
        mBluetoothAdapter.setName(String.format("%s-%s", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID), PROTO_UUID));
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Discoverable.makeDiscoverable(MainActivity.this, mBluetoothAdapter, DISCOVERABLE_TIMEOUT);
        }

        Log.i("bluetooth", "name " + mBluetoothAdapter.getName());
        Log.i("bluetooth", "mac address " + mBluetoothAdapter.getAddress());
        binding.setMac(mBluetoothAdapter.getAddress());
        snakeBar(mBluetoothAdapter.getAddress());
        mBluetoothAdapter.startDiscovery();
        if (ENABLE_SERVER_MODE) {
            UUID uuid = new ServiceUuidGenerator(PROTO_UUID).generate(mBluetoothAdapter.getAddress());
            AcceptThread acceptThread = new AcceptThread(uuid);
            serverExecutor.execute(acceptThread);
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

    private void rmAllConnexions() {
        while (!connexionMapping.entrySet().isEmpty()){
            Iterator<Map.Entry<String, BluetoothSocket>> iterator = connexionMapping.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<String, BluetoothSocket> entry = iterator.next();
                rmConnexion(entry.getValue());
            }
        }

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
            rmAllConnexions();
            restartOurBluetoothServices();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void snakeBar(String text) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
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
                Log.i("bluetooth", "started Server thread with uuid " + uuid);
            } catch (IOException e) {
                Log.e("bluetooth", "error when starting Server thread with uuid" + uuid, e);
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
                    Log.e("bluetooth", "error during accept", e);
                    break;
                }catch (RejectedExecutionException e){
                    Log.e("bluetooth", "socketExecutor can't handle this", e);
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

            try{
                socketExecutor.execute(new SocketLogic(socket));
            }catch (RejectedExecutionException e){
                Log.e("bluetooth", "socketExecutor can't handle this", e);
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
            try {
                if (!addConnexion(socket)) {
                    return;
                }
                connected = true;
                SocketWrapper socketW = new SocketWrapper(socket);
                mBluetoothAdapter.cancelDiscovery();
                socketW.send(new Hello());
                while (connected) {
                    try {
                        ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
                        Object o = inStream.readObject();
                        if (o instanceof RoutingMessage) {
                            Log.d("routing", "recv " + o);
                            ((RoutingMessage) o).accept(routingAlgo, socketW);
                            binding.setRoutingTableAsString(routingAlgo.toString());
                            //snakeBar("table :" + devices);
                        } else {
                            snakeBar(o.getClass().getSimpleName() + " from " + socket.getRemoteDevice().getName());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        connected = false;
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                Log.e("bluetooth socket", "", e);
            } finally {
                rmConnexion(socket);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                connected = false;
            }
        }
    }

    @NonNull
    private static ThreadPoolExecutor newSocketExecutor() {
        return new ThreadPoolExecutor(0, MAX_CONN,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(1));
    }

    @NonNull
    private static ExecutorService newServerExecutor() {
        return Executors.newSingleThreadExecutor();
    }


    @NonNull
    private ExecutorService newConnectExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
