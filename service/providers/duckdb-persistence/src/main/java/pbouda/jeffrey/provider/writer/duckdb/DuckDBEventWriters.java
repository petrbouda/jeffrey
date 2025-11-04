package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.provider.api.DatabaseWriter;
import pbouda.jeffrey.provider.api.EventWriters;
import pbouda.jeffrey.provider.api.model.writer.EventFrameWithHash;
import pbouda.jeffrey.provider.writer.duckdb.writer.*;

import javax.sql.DataSource;

public class DuckDBEventWriters implements EventWriters {
    private final DuckDBEventWriter eventWriter;
    private final DuckDBEventTypeWriter eventTypeWriter;
    private final DuckDBStacktraceWriter stacktraceWriter;
    private final DuckDBThreadWriter threadWriter;
    private final DuckDBFrameWriter frameWriter;

    public DuckDBEventWriters(DataSource dataSource, String profileId, int batchSize) {
        AsyncSingleWriter writer = new AsyncSingleWriter();
        this.eventWriter = new DuckDBEventWriter(writer, dataSource, profileId, batchSize);
        this.eventTypeWriter = new DuckDBEventTypeWriter(writer, dataSource, profileId, batchSize);
        this.stacktraceWriter = new DuckDBStacktraceWriter(writer, dataSource, profileId, batchSize);
        this.threadWriter = new DuckDBThreadWriter(writer, dataSource, profileId, batchSize);
        this.frameWriter = new DuckDBFrameWriter(writer, dataSource, profileId, batchSize);
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
