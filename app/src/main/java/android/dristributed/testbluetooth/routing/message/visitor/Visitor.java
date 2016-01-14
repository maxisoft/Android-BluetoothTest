package android.dristributed.testbluetooth.routing.message.visitor;


import android.dristributed.testbluetooth.routing.SocketWrapper;
import android.dristributed.testbluetooth.routing.message.Hello;
import android.dristributed.testbluetooth.routing.message.LinkDown;
import android.dristributed.testbluetooth.routing.message.LinkStateUpdate;
import android.dristributed.testbluetooth.routing.message.SendTo;

import java.io.IOException;


public interface Visitor {
    void visit(Hello message, SocketWrapper from);
    void visit(LinkStateUpdate message, SocketWrapper from);
    void visit(LinkDown message, SocketWrapper from);
    void visit(SendTo message, SocketWrapper from);
}
