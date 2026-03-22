package pbouda.jeffrey.provider.profile.query;

import pbouda.jeffrey.shared.common.model.EventTypeName;

public class DuckDBNativeSubSecondQueries implements ComplexQueries.SubSecond {

    private static final DuckDBSubSecondQueries QUERIES = DuckDBSubSecondQueries.of(
            EventTypeName.MALLOC, DuckDBNativeFlamegraphQueries.FREE_EVENT_EXISTS);

    @Override
    public String simple(boolean useWeight) {
        return QUERIES.simple(useWeight);
    }
}
