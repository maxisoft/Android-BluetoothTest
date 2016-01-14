package android.dristributed.testbluetooth.routing.message.visitor;


import android.dristributed.testbluetooth.routing.SocketWrapper;

public interface Visitable {
    public void accept(Visitor visitor, SocketWrapper from);
}
