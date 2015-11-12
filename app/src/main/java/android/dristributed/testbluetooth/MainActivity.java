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
import android.os.ParcelUuid;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.util.HashMap;
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<BluetoothDevice, ConnectThread> connectThreadMapping = new HashMap<>();
    private final Queue<Runnable> onDiscoveryFinishQueue = new ConcurrentLinkedQueue<>();
    BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mReceiver;
    private volatile boolean scanning;
    private AcceptThread acceptThread;

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
                        if (device != null && !connectThreadMapping.containsKey(device)) {
                            onDiscoveryFinishQueue.add(() -> new UuidsWithSdp(device).fetchUuidsWithSdp());
                        }
                    }
                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                    scanning = true;

                } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                    scanning = false;
                    boolean endOfQueue = false;
                    while (!endOfQueue) {
                        Runnable runnable = onDiscoveryFinishQueue.poll();
                        if (runnable != null) {
                            executor.execute(runnable);
                        } else {
                            endOfQueue = true;
                        }
                    }

                    handler.postDelayed(mBluetoothAdapter::startDiscovery, 10_000);


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

                } else if (action.equals(UuidsWithSdp.ACTION_UUID)) { // When a device's uuid lookup ended
                    BluetoothDevice device = intent.getParcelableExtra(UuidsWithSdp.EXTRA_DEVICE);
                    synchronized (connectThreadMapping) {
                        if (device != null && !connectThreadMapping.containsKey(device)) {
                            ParcelUuid[] uuids = new UuidsWithSdp(device).getUuids();
                            boolean found = false;
                            if (uuids != null && uuids.length > 0) {
                                for (ParcelUuid parcelUuid : uuids) {
                                    if (PROTO_UUID.equals(parcelUuid.getUuid())) {
                                        ConnectThread connectThread = new ConnectThread(device);
                                        connectThreadMapping.put(device, connectThread);
                                        executor.execute(connectThread);
                                        found = true;
                                        break;
                                    }
                                }
                                if (found) {
                                    snakeBar("Found matching service on " + device.getName());
                                } else {
                                    Log.i("bluetooth", "No matching service on " + device.getName());
                                }

                            } else {
                                Log.i("bluetooth", "No service on " + device.getName());
                            }
                        }
                    }

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
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Discoverable.makeDiscoverable(MainActivity.this, mBluetoothAdapter, DISCOVERABLE_TIMEOUT);
        }
        acceptThread = new AcceptThread();
        executor.execute(acceptThread);
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

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("PROJET", PROTO_UUID);
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
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

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

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createInsecureRfcommSocketToServiceRecord(PROTO_UUID);
            } catch (IOException e) {
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

            try {
                mmSocket.getOutputStream().write(("hello from " + mBluetoothAdapter.getName()).getBytes());
                mmSocket.close();
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
