package cafe.jeffrey.provider.profile.query;

public record SimpleComplexQueries(
        Flamegraph flamegraph,
        Timeseries timeseries,
        SubSecond subSecond) implements ComplexQueries {
}
