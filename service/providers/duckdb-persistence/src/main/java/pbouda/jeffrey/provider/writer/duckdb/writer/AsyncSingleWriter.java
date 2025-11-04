package pbouda.jeffrey.provider.writer.duckdb.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AsyncSingleWriter {

    private final Executor executor;

    private static class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingUncaughtExceptionHandler.class);

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            LOG.error("Uncaught exception: thread={} message={}", t.getName(), e.getMessage(), e);
        }
    }

    private static final ThreadFactory threadFactory = Thread.ofPlatform()
            .daemon(true)
            .name("async-duckdb-writer")
            .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
            .factory();

    public AsyncSingleWriter() {
        this(Executors.newSingleThreadExecutor(threadFactory));
    }

    public AsyncSingleWriter(Executor executor) {
        this.executor = executor;
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }
}
