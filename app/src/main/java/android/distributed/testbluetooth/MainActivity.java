package android.distributed.testbluetooth;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.distributed.ezbluetooth.BlockingApi;
import android.distributed.ezbluetooth.listener.EZBluetoothListener;
import android.distributed.ezbluetooth.listener.RegisterListener;
import android.distributed.ezbluetooth.routing.BluetoothRoutingRecord;
import android.distributed.ezbluetooth.routing.BluetoothRoutingTable;
import android.distributed.testbluetooth.databinding.MainActivityBinding;
import android.distributed.ezbluetooth.EZBluetoothService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private EZBluetoothService.Binder mService;
    private MainActivityBinding mBinding;
    private BlockingApi api;
    private ServiceConnection mConnexion = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = (EZBluetoothService.Binder) service;
            mBinding.setMac(mService.getMacAddress());
            mService.startDiscovery();
            api = new BlockingApi(MainActivity.this, mService);
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        mBinding.setConnexion(new AtomicInteger(0));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(view -> mService.startDiscovery());
        Intent intent = new Intent(this, EZBluetoothService.class);
        bindService(intent, mConnexion, Context.BIND_AUTO_CREATE);

        RegisterListener.register(this, new EZBluetoothListener() {
            @Override
            public void onRecv(@NonNull String address, Serializable data) {
                Log.i("bluetooth", String.format("recv from %s : %s", address, data));
            }

            @Override
            public void onNewPeer(@NonNull String address) {
                Log.i("bluetooth", String.format("onNewPeer: %s", address));
            }

            @Override
            public void onPeerDisconnected(@NonNull String address) {
                Log.i("bluetooth", String.format("onPeerDisconnected: %s", address));
            }
        });

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(MainActivity.class.getSimpleName(), "updating view");
                if (mBinding != null && mService != null) {
                    try{
                        BluetoothRoutingTable routingTable = mService.getRoutingTable();
                        mBinding.setConnexion(new AtomicInteger(routingTable.getSize()));
                        mBinding.setRoutingTableAsString(routingTableToString(routingTable));
                    }catch (Exception e) {
                        Log.e("MainActivity", "", e);
                    }
                }
                new Handler().postDelayed(this, 500);
            }
        }, 500);
    }

    public String routingTableToString(BluetoothRoutingTable routingTable) {
        String ret = "";
        for (String mac : routingTable) {
            ret += "\n";
            BluetoothRoutingRecord record = routingTable.getRecord(mac);
            ret += String.format("|----(%s)--(%d)--> %s",
                    record.getDoor().getRemoteMac(), record.getWeight().getWeight(), mac);
        }
        return ret.trim();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onPause() {
        if (mService != null) {
            mService.stopDiscovery();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mService != null) {
            mService.startDiscovery();
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (mService != null) {
            mService.stopDiscovery();
        }
        unbindService(mConnexion);
        RegisterListener.unregister(this);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void snakeBar(String text) {
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }
}
