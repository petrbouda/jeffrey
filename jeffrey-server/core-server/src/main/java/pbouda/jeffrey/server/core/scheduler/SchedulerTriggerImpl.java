package pbouda.jeffrey.server.core.scheduler;

import org.springframework.beans.factory.ObjectFactory;

import java.util.concurrent.CompletableFuture;

public class SchedulerTriggerImpl implements SchedulerTrigger {

    private final ObjectFactory<Scheduler> scheduler;
    private final Job job;

    public SchedulerTriggerImpl(ObjectFactory<Scheduler> scheduler, Job job) {
        this.scheduler = scheduler;
        this.job = job;
    }

    @Override
    public CompletableFuture<Void> execute() {
        return scheduler.getObject().submit(job, JobContext.EMPTY);
    }

    public CompletableFuture<Void> execute(JobContext context) {
        return scheduler.getObject().submit(job, context);
    }
}
