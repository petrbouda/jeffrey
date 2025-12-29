package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.shared.model.EventTypeName;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

public class DuckDBNativeTimeseriesQueries implements ComplexQueries.Timeseries {

    private static final DuckDBTimeseriesQueries QUERIES = DuckDBTimeseriesQueries.of(
            EventTypeName.MALLOC, DuckDBNativeFlamegraphQueries.FREE_EVENT_EXISTS);

    @Override
    public String simple(boolean useWeight, boolean useSpecifiedThread) {
        return QUERIES.simple(useWeight, useSpecifiedThread);
    }

    @Override
    public String simpleSearch(boolean useWeight, boolean useSpecifiedThread) {
        return QUERIES.simpleSearch(useWeight, useSpecifiedThread);
    }

    @Override
    public String filterable(boolean useWeight) {
        return QUERIES.filterable(useWeight);
    }

    @Override
    public String frameBased(boolean useWeight) {
        return QUERIES.frameBased(useWeight);
    }
}
