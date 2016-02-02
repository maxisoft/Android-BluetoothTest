package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

import java.io.Serializable;

public class SendTo implements RoutingMessage {
    private final short seq;
    private final String from;
    private final String to;
    private final Serializable data;
    private byte hop = 20;

    public SendTo(short seq, String from, String to, Serializable data) {
        this.seq = seq;
        this.from = from;
        this.to = to;
        this.data = data;
    }

    public short getSeq() {
        return seq;
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

    public byte getHop() {
        return hop;
    }

    public void setHop(byte hop) {
        this.hop = hop;
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
