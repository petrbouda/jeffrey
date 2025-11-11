package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.common.model.EventTypeName;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

public class DuckDBNativeSubSecondQueries implements ComplexQueries.SubSecond {

    private static final DuckDBSubSecondQueries QUERIES = DuckDBSubSecondQueries.of(
            EventTypeName.MALLOC, DuckDBNativeFlamegraphQueries.FREE_EVENT_EXISTS);

    @Override
    public String simple(boolean useWeight) {
        return QUERIES.simple(useWeight);
    }
}
