package pbouda.jeffrey.provider.profile.query;

import pbouda.jeffrey.shared.common.model.EventTypeName;

public class DuckDBNativeFlamegraphQueries implements ComplexQueries.Flamegraph {

    //language=SQL
    public static final String FREE_EVENT_EXISTS = """
            AND NOT EXISTS (
                SELECT 1 FROM events eFree
                WHERE eFree.event_type = 'profiler.Free'
                  AND e.weight_entity = eFree.weight_entity
            )
            """;

    private static final DuckDBFlamegraphQueries QUERIES =
            DuckDBFlamegraphQueries.of(EventTypeName.MALLOC, FREE_EVENT_EXISTS);

    @Override
    public String simple() {
        return QUERIES.simple();
    }

    @Override
    public String byWeight() {
        return QUERIES.byWeight();
    }

    @Override
    public String byThread() {
        return QUERIES.byThread();
    }

    @Override
    public String byThreadAndWeight() {
        return QUERIES.byThreadAndWeight();
    }
}
