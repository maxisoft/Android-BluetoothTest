package android.dristributed.testbluetooth.message;


public class AlreadyConnected extends AbstractMessage{
    final String socketServerMac;
    final String socketClientMac;

    public AlreadyConnected(String socketServerMac, String socketClientMac) {
        this.socketServerMac = socketServerMac;
        this.socketClientMac = socketClientMac;
    }

    public String getSocketServerMac() {
        return socketServerMac;
    }

    public String getSocketClientMac() {
        return socketClientMac;
    }
}
