package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

import java.io.Serializable;

public class SendTo implements RoutingMessage {
    private final String from;
    private final String to;
    private final Serializable data;
    private short count = 20;

    public SendTo(String from, String to, Serializable data) {
        this.from = from;
        this.to = to;
        this.data = data;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public Serializable getData() {
        return data;
    }

    public short getCount() {
        return count;
    }

    public void setCount(short count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "SendTo{" +
                "from=" + from +
                ", to=" + to +
                ", data=" + data +
                '}';
    }

    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        visitor.visit(this, from);
    }
}
