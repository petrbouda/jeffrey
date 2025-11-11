package pbouda.jeffrey.provider.writer.duckdb;

import pbouda.jeffrey.common.model.EventTypeName;
import pbouda.jeffrey.provider.writer.sql.query.ComplexQueries;

public class DuckDBNativeTimeseriesQueries implements ComplexQueries.Timeseries {

    private static final DuckDBTimeseriesQueries QUERIES = DuckDBTimeseriesQueries.of(
            EventTypeName.MALLOC, DuckDBNativeFlamegraphQueries.FREE_EVENT_EXISTS);

    @Override
    public String simple(boolean useWeight) {
        return QUERIES.simple(useWeight);
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
