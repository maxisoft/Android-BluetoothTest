package android.distributed.ezbluetooth.routing.exception;


import android.support.annotation.NonNull;

public class NoRouteToHost extends RuntimeException {
    @NonNull
    private final String from;
    @NonNull
    private final String to;

    public NoRouteToHost(@NonNull String from, @NonNull String to) {
        super("no route from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    @NonNull
    public String getFrom() {
        return from;
    }

    @NonNull
    public String getTo() {
        return to;
    }
}
