package android.distributed.ezbluetooth.routing;


import android.distributed.ezbluetooth.routing.exception.NoRouteToHost;
import android.distributed.ezbluetooth.routing.message.Hello;
import android.distributed.ezbluetooth.routing.message.LinkDown;
import android.distributed.ezbluetooth.routing.message.LinkStateUpdate;
import android.distributed.ezbluetooth.routing.message.SendTo;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RoutingAlgo implements Visitor, Iterable<String> {

    public static final int SPREADLINKSTATEUPDATE_LATENCY = 750;
    private final Set<SocketWrapper> linkStateUpdateQueue = new HashSet<>();
    private final Object routeTableReadUpdateLock = new Object();
    private final String localMacAddress;
    private BluetoothRoutingTable routingTable = new BluetoothRoutingTable();
    private ScheduledExecutorService scheduledThreadPool;

    public RoutingAlgo(@NonNull String localMacAddress) {
        this.localMacAddress = localMacAddress;
        scheduledThreadPool = Executors.newScheduledThreadPool(2);
    }

    protected RoutingAlgo(Parcel in) {
        this(in.readString());
        routingTable = (BluetoothRoutingTable) in.readSerializable();
    }

    private boolean updateRouteTable(String from, SocketWrapper door, BluetoothConnexionWeight weight, String updatedFrom) {
        synchronized (routeTableReadUpdateLock) {
            BluetoothRoutingRecord record = new BluetoothRoutingRecord(door, weight, updatedFrom);
            BluetoothRoutingRecord originalRecord = routingTable.updateRoute(from, record);
            return originalRecord == null || !record.equals(originalRecord);
        }
    }

    public boolean send(String node, Object data) throws IOException {
        BluetoothRoutingRecord record;
        synchronized (routeTableReadUpdateLock) {
            record = routingTable.getRecord(node);
        }
        if (record == null) {
            return false;
        }
        record.getDoor().send(new SendTo(localMacAddress, node, data));
        return true;
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
            //TODO use android handle ?
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
            for (final String device : peerRoutingTable) {
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
            //TODO use android handle ?
            scheduledThreadPool.schedule(new SpreadLinkStateUpdate(), SPREADLINKSTATEUPDATE_LATENCY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void visit(LinkDown message, SocketWrapper from) {
        String remoteMac = from.getRemoteMac();
        synchronized (routeTableReadUpdateLock) {
            BluetoothRoutingRecord currentRecord = routingTable.getRecord(message.getDeviceAddress());
            if (currentRecord != null && currentRecord.getUpdatedFrom().equals(remoteMac)) {
                removeRoute(remoteMac, from);
            }
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

    private void removeRoute(String deviceAddress, @Nullable SocketWrapper doNotUpdate) {
        BluetoothRoutingRecord oldRecord;
        synchronized (routeTableReadUpdateLock) {
            oldRecord = routingTable.updateRoute(deviceAddress, null);
            for (String mac : routingTable) {
                BluetoothRoutingRecord record = routingTable.getRecord(mac);
                if (deviceAddress.equals(record.getDoor().getRemoteMac())) {
                    routingTable.updateRoute(mac, null);
                }
            }
        }
        if (oldRecord != null) {
            //send it to close nodes
            for (SocketWrapper socketWrapper : getClosestSockets()) {
                if (socketWrapper != doNotUpdate) {
                    try {
                        socketWrapper.send(new LinkDown(deviceAddress));
                    } catch (IOException e) {
                        handleSendError(socketWrapper, e);
                    }
                }
            }
        }

    }

    public void removeRoute(String deviceAddress) {
        removeRoute(deviceAddress, null);
    }

    @Override
    public void visit(final SendTo message, SocketWrapper door) {
        if (localMacAddress.equals(message.getTo())) {
            //pushSendToMessage(message); //TODO return Message
        } else {
            BluetoothRoutingRecord record;
            synchronized (routeTableReadUpdateLock) {
                record = routingTable.getRecord(message.getTo());
            }
            if (record == null) {
                throw new NoRouteToHost(localMacAddress, message.getTo());
            }
            try {
                record.getDoor().send(message);
            } catch (IOException e) {
                handleSendError(record.getDoor(), e);
            }
        }
    }

    protected void handleSendError(SocketWrapper socket, IOException e) {
        try {
            socket.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        removeRoute(socket.getRemoteMac());

        e.printStackTrace();
    }

    @Override
    public Iterator<String> iterator() {
        return routingTable.iterator();
    }

    public void stop() {
        scheduledThreadPool.shutdownNow();
        linkStateUpdateQueue.clear();
    }

    public BluetoothRoutingTable getRoutingTableCopy() {
        return (BluetoothRoutingTable) routingTable.clone();
    }

    @Override
    public String toString() {
        String ret = "";
        for (String mac : routingTable) {
            ret += "\n";
            BluetoothRoutingRecord record = routingTable.getRecord(mac);
            ret += String.format("|----(%s)--(%d)--> %s",
                    record.getDoor().getRemoteMac(), record.getWeight().getWeight(), mac);
        }
        return ret.trim();
    }

    class SpreadLinkStateUpdate implements Runnable {

        @Override
        public void run() {
            synchronized (routeTableReadUpdateLock) {
                for (SocketWrapper door : linkStateUpdateQueue) {
                    try {
                        door.send(new LinkStateUpdate(routingTable));
                    } catch (IOException e) {
                        handleSendError(door, e);
                    }
                }
                linkStateUpdateQueue.clear();
            }
        }
    }
}
