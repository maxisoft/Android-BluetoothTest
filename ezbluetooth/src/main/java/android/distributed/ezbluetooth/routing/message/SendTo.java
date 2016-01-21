package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

public class SendTo implements RoutingMessage {
    private final String from;
    private final String to;
    private final Object data;
    private short count;

    public SendTo(String from, String to, Object data) {
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

    public Object getData() {
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
