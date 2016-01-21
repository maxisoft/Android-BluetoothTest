package android.distributed.testbluetooth.message;


import java.io.Serializable;

public class SendTo<T extends Serializable> {
    private final String from;
    private final String to;
    private final T data;

    public SendTo(String from, String to, T data) {
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

    public T getData() {
        return data;
    }
}
