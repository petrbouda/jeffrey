package pbouda.jeffrey.shared;

import java.time.Duration;
import java.util.concurrent.*;

public abstract class CompletableFutures {

    public static <T> CompletableFuture<T> from(
            Future<T> future, ScheduledExecutorService scheduler, Duration pollInterval) {

        CompletableFuture<T> cf = new CompletableFuture<>();
        Runnable poller = new Runnable() {
            @Override
            public void run() {
                if (future.isDone()) {
                    try {
                        cf.complete(future.get());
                    } catch (ExecutionException e) {
                        cf.completeExceptionally(e.getCause());
                    } catch (InterruptedException e) {
                        cf.completeExceptionally(e);
                    }
                } else if (future.isCancelled()) {
                    cf.cancel(false);
                } else {
                    scheduler.schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        };
        scheduler.execute(poller);
        return cf;
    }
}
