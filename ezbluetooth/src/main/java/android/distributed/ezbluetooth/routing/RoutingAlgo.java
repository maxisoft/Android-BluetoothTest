package android.distributed.ezbluetooth.routing;


import android.distributed.ezbluetooth.routing.exception.JumpLimit;
import android.distributed.ezbluetooth.routing.exception.NoRouteToHost;
import android.distributed.ezbluetooth.routing.message.ACK;
import android.distributed.ezbluetooth.routing.message.Hello;
import android.distributed.ezbluetooth.routing.message.LinkDown;
import android.distributed.ezbluetooth.routing.message.LinkStateUpdate;
import android.distributed.ezbluetooth.routing.message.SendTo;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RoutingAlgo implements Visitor, Iterable<String> {

    public static final int SPREADLINKSTATEUPDATE_LATENCY = 750;
    public static final String TAG = RoutingAlgo.class.getSimpleName();
    private final Set<SocketWrapper> linkStateUpdateQueue = new HashSet<>();
    private final Object routeTableReadUpdateLock = new Object();
    private final String localMacAddress;
    private BluetoothRoutingTable routingTable = new BluetoothRoutingTable();

    private ScheduledExecutorService scheduledThreadPool;
    private ExecutorService sendExecutor;

    private RecvListener recvListener;
    private PeerListener peerListener;

    private final AtomicLong seqGenerator = new AtomicLong();
    private AckListener ackListener;

    public RoutingAlgo(@NonNull String localMacAddress) {
        this.localMacAddress = localMacAddress;
        scheduledThreadPool = Executors.newScheduledThreadPool(1);
        sendExecutor = Executors.newFixedThreadPool(2);

        scheduledThreadPool.scheduleAtFixedRate(new PeriodicUpdateTable(), 1, 5, TimeUnit.SECONDS);
    }

    private short nextSeq() {
        long l = seqGenerator.getAndIncrement();
        seqGenerator.compareAndSet(Long.MAX_VALUE, 0);
        return (short) (l % Short.MAX_VALUE);
    }

    private boolean updateRouteTable(String to, SocketWrapper door, BluetoothConnexionWeight weight, String updatedFrom) {
        synchronized (routeTableReadUpdateLock) {
            BluetoothRoutingRecord record = new BluetoothRoutingRecord(door, weight, updatedFrom);
            BluetoothRoutingRecord originalRecord = routingTable.updateRoute(to, record);
            if (originalRecord == null && peerListener != null) {
                peerListener.onPeerAdded(to);
            }
            return originalRecord == null || !record.equals(originalRecord);
        }
    }

    private void removeRoute(String deviceAddress, @Nullable SocketWrapper doNotUpdate) {
        Object removed;
        synchronized (routeTableReadUpdateLock) {
            removed = routingTable.updateRoute(deviceAddress, null);
            //cascade the changes
            //work around for ConcurrentModificationException
            boolean changes = true;
            while (changes) {
                changes = false;
                for (String mac : routingTable) {
                    BluetoothRoutingRecord record = routingTable.getRecord(mac);
                    if (deviceAddress.equals(record.getDoor().getRemoteMac())) {
                        Object removedNested = routingTable.updateRoute(mac, null);
                        if (removedNested != null && peerListener != null) {
                            peerListener.onPeerRemoved(mac);
                        }
                        changes = true;
                        break;
                    }
                }
            }
        }

        if (removed != null && peerListener != null) {
            peerListener.onPeerRemoved(deviceAddress);
        }

        //send link dead event to closest nodes
        for (SocketWrapper socket : getClosestSockets()) {
            if (socket != doNotUpdate) {
                send(socket, new LinkDown(deviceAddress));
            }
        }
    }



    public void removeRoute(String deviceAddress) {
        removeRoute(deviceAddress, null);
    }

    public short send(String node, Serializable data) {
        BluetoothRoutingRecord record;
        synchronized (routeTableReadUpdateLock) {
            record = routingTable.getRecord(node);
        }
        if (record == null) {
            return -1;
        }
        short seq = nextSeq();
        send(record.getDoor(), new SendTo(seq, localMacAddress, node, data));
        return seq;
    }

    private Future send(SocketWrapper socket, Serializable data) {
        return sendExecutor.submit(() -> {
            try {
                socket.send(data);
            } catch (IOException e) {
                handleSendError(socket, e);
            }
        });
    }


    @Override
    public void visit(Hello message, SocketWrapper door) {
        boolean updated = false;
        final String remoteMac = door.getRemoteMac();
        synchronized (routeTableReadUpdateLock) {
            if (updateRouteTable(remoteMac, door, new BluetoothConnexionWeight(1), remoteMac)) {
                updated = true;
                for (final String device : routingTable) {
                    SocketWrapper otherDoor = routingTable.getRecord(device).getDoor();
                    linkStateUpdateQueue.add(otherDoor);
                }
            }
        }
        if (updated) {
            scheduledThreadPool.schedule(new SpreadLinkStateUpdate(), SPREADLINKSTATEUPDATE_LATENCY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void visit(LinkStateUpdate message, SocketWrapper door) {
        BluetoothRoutingTable peerRoutingTable = message.getRoutingTable();
        boolean updated = false;
        int basePathWeight = 1;
        final String remoteMac = door.getRemoteMac();
        synchronized (routeTableReadUpdateLock) {

            new Hello().accept(this, door);

            for (final String device : peerRoutingTable) {
                if (device.equals(localMacAddress)) {
                    continue;
                }
                BluetoothRoutingRecord currentRecord = routingTable.getRecord(device);
                BluetoothRoutingRecord peerRecord = peerRoutingTable.getRecord(device);
                if (peerRecord != null && peerRecord.getUpdatedFrom().equals(localMacAddress)) {
                    //do not update (avoid routing loops)
                    continue;
                }
                Long currentWeight = currentRecord != null ? currentRecord.getWeight().getWeight() : null;
                if (currentWeight == null) {
                    currentWeight = Long.MAX_VALUE;
                }
                Long peerWeight = peerRecord != null ? peerRecord.getWeight().getWeight() : null;
                if (peerWeight != null && basePathWeight + peerWeight < currentWeight) {
                    if (updateRouteTable(device, door, new BluetoothConnexionWeight(basePathWeight + peerWeight), remoteMac)) {
                        updated = true;
                        for (final String deviceMac : routingTable) {
                            SocketWrapper otherDoor = routingTable.getRecord(deviceMac).getDoor();
                            if (otherDoor != null && !door.equals(otherDoor)) {
                                linkStateUpdateQueue.add(otherDoor);
                            }
                        }
                    }
                }
            }
        }
        if (updated) {
            scheduledThreadPool.schedule(new SpreadLinkStateUpdate(), SPREADLINKSTATEUPDATE_LATENCY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void visit(LinkDown message, SocketWrapper from) {
        String remoteMac = from.getRemoteMac();
        boolean comm = false;
        synchronized (routeTableReadUpdateLock) {
            BluetoothRoutingRecord currentRecord = routingTable.getRecord(message.getDeviceAddress());
            if (currentRecord != null) {
                if (currentRecord.getUpdatedFrom().equals(remoteMac)) {
                    removeRoute(remoteMac, from);
                } else {
                    linkStateUpdateQueue.add(from);
                    comm = true;
                }
            }
        }
        if (comm) {
            scheduledThreadPool.schedule(new SpreadLinkStateUpdate(), SPREADLINKSTATEUPDATE_LATENCY, TimeUnit.MILLISECONDS);
        }
    }

    public List<SocketWrapper> getClosestSockets() {
        List<SocketWrapper> ret = new ArrayList<>();
        synchronized (routeTableReadUpdateLock) {
            for (final String deviceMac : routingTable) {
                BluetoothRoutingRecord record = routingTable.getRecord(deviceMac);
                if (record.getWeight().getWeight() == 1) {
                    ret.add(record.getDoor());
                }
            }
        }
        return ret;
    }

    @Override
    public void visit(final SendTo message, SocketWrapper door) {
        if (localMacAddress.equals(message.getTo())) {
            if (message.getData() instanceof ACK) { //unwrap ACK message
                ((ACK) message.getData()).accept(this, door);
            } else {
                send(message.getFrom(), new ACK(message.getSeq())); //wrap ACK message
                if (recvListener != null) {
                    recvListener.onRecv(message.getFrom(), message.getData());
                }
            }
        } else {
            BluetoothRoutingRecord record;
            synchronized (routeTableReadUpdateLock) {
                record = routingTable.getRecord(message.getTo());
            }
            if (record == null) {
                throw new NoRouteToHost(localMacAddress, message.getTo());
            }
            if (message.getHop() > 0) {
                message.setHop((byte) (message.getHop() - 1));
                send(record.getDoor(), message);
            } else {
                throw new JumpLimit();
            }
        }
    }

    @Override
    public void visit(ACK message, SocketWrapper from) {
        if (ackListener != null) {
            ackListener.onAckRecv(message.getSeq());
        }
    }

    protected void handleSendError(SocketWrapper socket, IOException e) {
        Log.w(TAG, "handle send error", e);
        try {
            socket.close();
        } catch (IOException e1) {
            Log.e(TAG, "closing socket", e1);
        }

        removeRoute(socket.getRemoteMac());
    }

    @Override
    public Iterator<String> iterator() {
        return routingTable.iterator();
    }

    public void stop() {
        scheduledThreadPool.shutdownNow();
        sendExecutor.shutdownNow();
        linkStateUpdateQueue.clear();
    }

    public BluetoothRoutingTable getRoutingTable() {
        return routingTable;
    }

    public BluetoothRoutingTable getRoutingTableCopy() {
        synchronized (routeTableReadUpdateLock){
            return (BluetoothRoutingTable) routingTable.clone();
        }

    }

    public void setRecvListerner(@Nullable RecvListener listerner) {
        this.recvListener = listerner;
    }

    public void setPeerListener(@Nullable PeerListener peerListener) {
        this.peerListener = peerListener;
    }

    public void setAckListener(AckListener ackListener) {
        this.ackListener = ackListener;
    }

    public boolean hasRoute(String address) {
        synchronized (routeTableReadUpdateLock){
            return routingTable.getRecord(address) != null;
        }

    }

    public interface RecvListener {
        void onRecv(@NonNull String from, Object obj);
    }

    public interface PeerListener {
        void onPeerAdded(@NonNull String address);

        void onPeerRemoved(@NonNull String address);
    }

    public interface AckListener {
        void onAckRecv(short seq);
    }


    class PeriodicUpdateTable implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, PeriodicUpdateTable.class.getSimpleName());
            try{
                synchronized (routeTableReadUpdateLock) {
                    BluetoothRoutingTable routingTableCopy = getRoutingTableCopy();
                    LinkStateUpdate message = new LinkStateUpdate(routingTableCopy);
                    for (SocketWrapper socket : getClosestSockets()) {
                        send(socket, message);
                    }
                }
            }catch (Exception e){
                Log.e(TAG, "unexpected in " + PeriodicUpdateTable.class.getSimpleName(), e);
            }

        }
    }

    class SpreadLinkStateUpdate implements Runnable {

        @Override
        public void run() {
            synchronized (routeTableReadUpdateLock) {
                BluetoothRoutingTable routingTableCopy = getRoutingTableCopy();
                LinkStateUpdate message = new LinkStateUpdate(routingTableCopy);
                for (SocketWrapper socket : linkStateUpdateQueue) {
                    send(socket, message);
                }
                linkStateUpdateQueue.clear();
            }
        }
    }
}
