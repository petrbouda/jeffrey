package pbouda.jeffrey.manual;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.flamegraph.FlameGraphProtoBuilder;
import pbouda.jeffrey.flamegraph.proto.FlamegraphData;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameBuilder;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.provider.platform.DuckDBDataSourceProvider;
import pbouda.jeffrey.provider.profile.query.DuckDBFlamegraphQueries;
import pbouda.jeffrey.provider.profile.query.FlamegraphRecordRowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

public class ManualApplication {
    static void main() {
        IO.println("Manual tests");

        DuckDBDataSourceProvider dataSourceProvider = new DuckDBDataSourceProvider();
        DataSource datasource = dataSourceProvider.database(Map.of(
                "url", "jdbc:duckdb:/Users/petrbouda/.jeffrey/jeffrey-data.db",
                "pool-size", "10"
        ));

        String flamegraphSql = DuckDBFlamegraphQueries.of().simple();

        var client = new NamedParameterJdbcTemplate(datasource);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", "019aa845-b71e-7a0c-8138-fa35df42737e")
                .addValue("event_type", "jdk.ExecutionSample")
                .addValue("from_time", null)
                .addValue("to_time", null)
                .addValue("stacktrace_types", null)
                .addValue("included_tags", null)
                .addValue("excluded_tags", null);

        long start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            execute(client, flamegraphSql, params);
        }
        long end = System.nanoTime();

        IO.println("Total took: " + ((end - start) / 1_000_000) + " ms");
    }

    private static void execute(NamedParameterJdbcTemplate client, String flamegraphSql, MapSqlParameterSource params) {
        long start = System.nanoTime();
        List<FlamegraphRecord> records = client.query(
                flamegraphSql, params, new FlamegraphRecordRowMapper(Type.EXECUTION_SAMPLE));
        long end = System.nanoTime();
        IO.println("Query took: " + ((end - start) / 1_000_000) + " ms");
        IO.println("Records: " + records.size());

        long startBuilding = System.nanoTime();
        FrameBuilder builder = new FrameBuilder(false, false, false, null);
        for (FlamegraphRecord record : records) {
            builder.onRecord(record);
        }
        Frame rootFrame = builder.build();
        long endBuilding = System.nanoTime();
        IO.println("Building took: " + ((endBuilding - startBuilding) / 1_000_000) + " ms");
        IO.println("Root frame: " + rootFrame);

        long startBuildingJson = System.nanoTime();
        FlameGraphProtoBuilder jsonBuilder = new FlameGraphProtoBuilder(false, false, weight -> weight + " millis");
        FlamegraphData data = jsonBuilder.build(rootFrame);
        System.out.println(data.getDepth());
        long endBuildingJson = System.nanoTime();
        IO.println("JSON Building took: " + ((endBuildingJson - startBuildingJson) / 1_000_000) + " ms");
    }
}
