package pbouda.jeffrey;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {

    private final AtomicInteger sequence = new AtomicInteger(1);
    private final String prefix;

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setName(prefix + "-" + sequence.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    }
}
