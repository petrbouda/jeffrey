package pbouda.jeffrey.provider.writer.sql.query;

public record SimpleComplexQueries(
        Flamegraph flamegraph,
        Timeseries timeseries,
        SubSecond subSecond) implements ComplexQueries {
}
