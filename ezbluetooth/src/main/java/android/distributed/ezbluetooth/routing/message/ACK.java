package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

public class ACK implements RoutingMessage{
    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        //visitor.visit(this); // TODO
    }
}
