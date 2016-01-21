package android.distributed.ezbluetooth.routing.message.visitor;


import android.distributed.ezbluetooth.routing.SocketWrapper;

public interface Visitable {
    public void accept(Visitor visitor, SocketWrapper from);
}
