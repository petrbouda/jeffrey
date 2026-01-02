package pbouda.jeffrey.platform.scheduler;

import java.util.concurrent.CompletableFuture;

public interface SchedulerTrigger {

    CompletableFuture<Void> execute(JobContext context);

    default CompletableFuture<Void> execute() {
        return execute(JobContext.EMPTY);
    }
}
