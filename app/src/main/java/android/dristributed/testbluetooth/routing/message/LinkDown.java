package android.dristributed.testbluetooth.routing.message;


import android.dristributed.testbluetooth.routing.SocketWrapper;
import android.dristributed.testbluetooth.routing.message.visitor.Visitor;

public class LinkDown implements RoutingMessage {

    private final String deviceAddress;

    public LinkDown(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        visitor.visit(this, from);
    }

}
