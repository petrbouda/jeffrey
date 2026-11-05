package jeffrey.testapp.server;

import org.springframework.util.CustomizableThreadCreator;

import java.util.concurrent.ThreadFactory;

public class NamedThreadFactory extends CustomizableThreadCreator implements ThreadFactory {

    public NamedThreadFactory(String prefix) {
        super(prefix);
        setDaemon(true);
    }

    public Thread newThread(Runnable runnable) {
        return this.createThread(runnable);
    }
}
