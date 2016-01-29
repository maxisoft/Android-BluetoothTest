package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

public class ACK implements RoutingMessage {
    private final short seq;

    public ACK(short seq) {
        this.seq = seq;
    }

    public short getSeq() {
        return seq;
    }

    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        visitor.visit(this, from);
    }
}
