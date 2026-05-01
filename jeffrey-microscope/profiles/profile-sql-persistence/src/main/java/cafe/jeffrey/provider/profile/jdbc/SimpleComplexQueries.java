package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

public record SimpleComplexQueries(
        Flamegraph flamegraph,
        Timeseries timeseries,
        SubSecond subSecond) implements ComplexQueries {
}
