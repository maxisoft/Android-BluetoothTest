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
import android.dristributed.testbluetooth.bluetooth.UuidsWithSdp;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT = 0xB1;
    public static final int DISCOVERABLE_TIMEOUT = 0;
    public static final UUID PROTO_UUID = UUID.fromString("117adc55-ab0f-4db5-939c-0de3926a3af7");
    public static final List<UUID> UUIDS = Arrays.asList(
            UUID.fromString("117adc55-ab0f-4db5-939c-0df6926a3af1"),
            UUID.fromString("117a9525-ab0f-4db5-939c-0de8166a3af2"),
            UUID.fromString("11721355-ab0f-4db5-939c-0d4156156af3"),
            UUID.fromString("11716c55-ab0f-4db5-939c-0d8466963af4"),
            UUID.fromString("117a5c55-ab0f-4db5-939c-0d15536a3af5"),
            UUID.fromString("11723c55-ab0f-4db5-939c-0d8251615af6"),
            UUID.fromString("117a9855-ab0f-4db5-939c-0d48926a3af7")
    );

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
    private final Map<BluetoothDevice, ConnectThread> connectThreadMapping = new HashMap<>();
    private final Map<BluetoothDevice, Long> dejaVu = Collections.synchronizedMap(new HashMap<>());
    private final Queue<Runnable> onDiscoveryFinishQueue = new ConcurrentLinkedQueue<>();
    BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mReceiver;
    private volatile boolean scanning;
    private Map<UUID, AcceptThread> acceptThreads = new HashMap<>();
    private Runnable startDiscoveryRunnable;

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
                        if (name != null && name.endsWith(PROTO_UUID.toString()) && !connectThreadMapping.containsKey(device)) {
                            Log.i("bluetooth", "found a device");
                            onDiscoveryFinishQueue.add(() -> {
                                for (UUID uuid : UUIDS) {
                                    try {
                                        mBluetoothAdapter.cancelDiscovery();
                                        Log.i("bluetooth", "trying to connect to " + name);
                                        ConnectThread connectThread = new ConnectThread(device, uuid);
                                        Log.i("bluetooth", "using " + connectThread);
                                        connectThreadMapping.put(device, connectThread);
                                        executor.execute(connectThread);
                                        snakeBar("Found matching service on " + name);
                                        break;
                                    } catch (Exception e) {
                                        Log.e("bluetooth", "when connecting to " + name, e);
                                    }
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
                    handler.postDelayed(startDiscoveryRunnable, 10_000);
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
        snakeBar(mBluetoothAdapter.getName());
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Discoverable.makeDiscoverable(MainActivity.this, mBluetoothAdapter, DISCOVERABLE_TIMEOUT);
        }


        for (UUID uuid : UUIDS) {
            AcceptThread acceptThread = new AcceptThread(uuid);
            acceptThreads.put(uuid, acceptThread);
            executor.execute(acceptThread);
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
        unregisterReceiver(mReceiver);
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
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("PROJET", uuid);
                Log.i("bluetooth", "started Server thread with uuid " + uuid);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    BluetoothSocket socket = mmServerSocket.accept();
                    if (socket != null) {
                        // Do work to manage the connection (in a separate thread)
                        executor.execute(() -> {
                            byte[] buff = new byte[256];
                            try {
                                socket.getInputStream().read(buff);
                                //socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Log.i("bluetooth", "RECEIVED " + new String(buff));
                            snakeBar(new String(buff));
                        });
                    }
                } catch (IOException e) {
                    break;
                }
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

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final UUID uuid;

        public ConnectThread(BluetoothDevice device, UUID uuid) throws IOException {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            this.uuid = uuid;
            BluetoothSocket tmp = null;
            mmDevice = device;

            mmSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();
            Log.i("bluetooth", "sending hello to " + mmDevice.getName());
            try {
                mmSocket.getOutputStream().write(("hello from " + mBluetoothAdapter.getName()).getBytes());
                mmSocket.getOutputStream().flush();
                //mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
