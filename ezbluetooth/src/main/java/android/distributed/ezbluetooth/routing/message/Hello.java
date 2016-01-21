package android.distributed.ezbluetooth.routing.message;


import android.distributed.ezbluetooth.routing.SocketWrapper;
import android.distributed.ezbluetooth.routing.message.visitor.Visitor;

/**
 * Hello messages are used as a form of greeting.
 * <p>Allow a router to discover other adjacent routers on its local links and networks
 */
public class Hello implements RoutingMessage {
    @Override
    public void accept(Visitor visitor, SocketWrapper from) {
        visitor.visit(this, from);
    }
}
