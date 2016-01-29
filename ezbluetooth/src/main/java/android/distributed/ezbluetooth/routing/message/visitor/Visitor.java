package android.distributed.ezbluetooth.routing.message.visitor;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.ACK;
import android.distributed.ezbluetooth.routing.message.Hello;
import android.distributed.ezbluetooth.routing.message.LinkDown;
import android.distributed.ezbluetooth.routing.message.LinkStateUpdate;
import android.distributed.ezbluetooth.routing.message.SendTo;

public interface Visitor {
    void visit(Hello message, SocketWrapper from);

    void visit(LinkStateUpdate message, SocketWrapper from);

    void visit(LinkDown message, SocketWrapper from);

    void visit(SendTo message, SocketWrapper from);

    void visit(ACK message, SocketWrapper from);
}
