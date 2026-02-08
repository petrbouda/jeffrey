package pbouda.jeffrey.manual;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.shared.persistence.SimpleJdbcDataSource;

import javax.sql.DataSource;
import java.util.List;

public class ManualTimeseriesApplication {
    static void main() {
        IO.println("Manual tests");

        String home = System.getProperty("user.home");
        DataSource datasource = new SimpleJdbcDataSource(
                "jdbc:duckdb:" + home + "/.jeffrey/jeffrey-data.db");

        String flamegraphSql = "----";

        var client = new NamedParameterJdbcTemplate(datasource);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", "019a913a-36ce-7758-8741-46c50a2527e9")
                .addValue("event_type", "jdk.ExecutionSample")
                .addValue("search_string", "jeffrey")
                .addValue("from_time", null)
                .addValue("to_time", null)
                .addValue("stacktrace_types", null)
                .addValue("included_tags", null)
                .addValue("excluded_tags", null);

        long start = System.nanoTime();
        for (int i = 0; i < 1; i++) {
            execute(client, flamegraphSql, params);
        }
        long end = System.nanoTime();

        IO.println("Total took: " + ((end - start) / 1_000_000) + " ms");
    }

    public record TimeseriesSearchRecord(long second, long total, long matched) {
    }

    private static void execute(NamedParameterJdbcTemplate client, String flamegraphSql, MapSqlParameterSource params) {
        long start = System.nanoTime();
        List<TimeseriesSearchRecord> records = client.query(
                flamegraphSql, params, (r, _) -> new TimeseriesSearchRecord(r.getLong("seconds"), r.getLong("total_value"), r.getLong("matched_value")));
        long end = System.nanoTime();
        IO.println("Query took: " + ((end - start) / 1_000_000) + " ms");
        IO.println("Records: " + records.size());

        System.out.println(records);

//        long startBuilding = System.nanoTime();
//        FrameBuilder builder = new FrameBuilder(false, false, false, null);
//        for (TimeseriesRecord record : records) {
//            builder.onRecord(record);
//        }
//        Frame rootFrame = builder.build();
//        long endBuilding = System.nanoTime();
//        IO.println("Building took: " + ((endBuilding - startBuilding) / 1_000_000) + " ms");
//        IO.println("Root frame: " + rootFrame);
//
//        long startBuildingJson = System.nanoTime();
//        FlameGraphBuilder jsonBuilder = new FlameGraphBuilder(false, false, weight -> weight + " millis");
//        FlamegraphData data = jsonBuilder.build(rootFrame);
//        System.out.println(data.depth());
//        long endBuildingJson = System.nanoTime();
//        IO.println("JSON Building took: " + ((endBuildingJson - startBuildingJson) / 1_000_000) + " ms");
    }
}
