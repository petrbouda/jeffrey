package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.profile.writer.*;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DuckDBEventWriters implements EventWriters {

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final DuckDBEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;

    public DuckDBEventWriters(DataSource dataSource, int batchSize) {
        this.eventWriter = new DuckDBEventWriter(EXECUTOR, dataSource, batchSize);
        this.eventTypeWriter = new DuckDBEventTypeWriter(EXECUTOR, dataSource, batchSize);
        this.stacktraceWriter = new DuckDBStacktraceWriter(EXECUTOR, dataSource, batchSize);
        this.threadWriter = new DuckDBThreadWriter(EXECUTOR, dataSource, batchSize);
        this.frameWriter = new DuckDBFrameWriter(EXECUTOR, dataSource, batchSize);
    }

    @Override
    public DuckDBEventWriter events() {
        return eventWriter;
    }

    @Override
    public DuckDBEventTypeWriter eventTypes() {
        return eventTypeWriter;
    }

    @Override
    public DuckDBStacktraceWriter stacktraces() {
        return stacktraceWriter;
    }

    @Override
    public DuckDBThreadWriter threads() {
        return threadWriter;
    }

    @Override
    public DatabaseWriter<EventFrameWithHash> frames() {
        return frameWriter;
    }

    @Override
    public void close() {
        eventTypeWriter.close();
        eventWriter.close();
        stacktraceWriter.close();
        threadWriter.close();
        frameWriter.close();
    }
}
