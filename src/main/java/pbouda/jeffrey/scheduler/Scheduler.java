package pbouda.jeffrey.scheduler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.NamedThreadFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(Scheduler.class);

    private static final Duration SHUTDOWN_DELAY = Duration.ofSeconds(10);
    private static final int POOL_SIZE = 8;
    private static final Duration DEFAULT_PERIOD;
    private static final ScheduledExecutorService EXECUTOR;

    private static final String ERROR_MESSAGE = """
            Some jobs didn't managed to finish themselves off.
            It can result in data inconsistency. Prefer re-generating
            sources files by remove impacted repositories.
            """;

    static {
        Config config = ConfigFactory.load();
        Config scheduler = config.getConfig("scheduler");
        DEFAULT_PERIOD = scheduler.getDuration("default_period");
        EXECUTOR = Executors.newScheduledThreadPool(POOL_SIZE,
                new NamedThreadFactory("scheduler"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                EXECUTOR.shutdown();
                boolean finalized = EXECUTOR.awaitTermination(
                        SHUTDOWN_DELAY.getSeconds(), TimeUnit.SECONDS);

                if (!finalized) {
                    LOG.error(ERROR_MESSAGE);
                }
            } catch (InterruptedException e) {
                LOG.error(ERROR_MESSAGE, e);
            }
        }));
    }

    /**
     * Execute a periodic task provided by <b>task</b> with
     * a fixed rate and delay equal to {@link #DEFAULT_PERIOD}.
     *
     * @param task a periodic task.
     */
    public static void periodic(Runnable task) {
        EXECUTOR.scheduleAtFixedRate(
                task,
                DEFAULT_PERIOD.getSeconds(),
                DEFAULT_PERIOD.getSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * Schedule a one-shot task for an execution as soon as possible.
     *
     * @param task a one-shot task.
     */
    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }
}
