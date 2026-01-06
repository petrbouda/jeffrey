/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.jmh.flamegraph;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameBuilder;
import pbouda.jeffrey.jmh.flamegraph.mapper.OptimizedFlamegraphRecordWithThreadsRowMapper;
import pbouda.jeffrey.jmh.flamegraph.utils.FramesCache;
import pbouda.jeffrey.jmh.flamegraph.verification.BenchmarkVerification;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.provider.profile.query.DuckDBFlamegraphQueries;
import pbouda.jeffrey.provider.profile.query.FlamegraphRecordWithThreadsRowMapper;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.persistence.SimpleJdbcDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class FlamegraphByThreadAndWeightBenchmark {

    private static final Path DATABASE_PATH = Path.of("jmh-tests/data/profile-data.db");
    private static final String EVENT_TYPE = "jdk.ExecutionSample";
    private static final String JDBC_URL = "jdbc:duckdb:" + DATABASE_PATH.toAbsolutePath();

    private static final MapSqlParameterSource QUERY_PARAMS = new MapSqlParameterSource()
            .addValue("event_type", EVENT_TYPE)
            .addValue("from_time", null)
            .addValue("to_time", null)
            .addValue("java_thread_id", null)
            .addValue("os_thread_id", null)
            .addValue("stacktrace_types", null)
            .addValue("included_tags", null)
            .addValue("excluded_tags", null);

    // Baseline supplier (SQL-side frame resolution)
    private static final Supplier<List<FlamegraphRecord>> BASELINE_INVOCATION = () -> {
        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().byThreadAndWeight();
        FlamegraphRecordWithThreadsRowMapper rowMapper = new FlamegraphRecordWithThreadsRowMapper(Type.EXECUTION_SAMPLE);
        return jdbcTemplate.query(sql, QUERY_PARAMS, rowMapper);
    };

    // Optimized supplier that uses cached frames
    private static final Supplier<List<FlamegraphRecord>> OPTIMIZED_INVOCATION = () -> {
        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().byThreadAndWeightOptimized();
        OptimizedFlamegraphRecordWithThreadsRowMapper rowMapper = new OptimizedFlamegraphRecordWithThreadsRowMapper(
                Type.EXECUTION_SAMPLE, FramesCache.load(ds), true);
        return jdbcTemplate.query(sql, QUERY_PARAMS, rowMapper);
    };

    private final BenchmarkVerification verification = new BenchmarkVerification("ByThreadAndWeightFlamegraphBenchmark");
    private Frame lastBuiltFrame;

    @TearDown(Level.Invocation)
    public void verifyInvocation() {
        verification.verify(lastBuiltFrame);
        lastBuiltFrame = null;
    }

    /**
     * Baseline: SQL-side frame resolution with thread and weight grouping.
     */
    @Benchmark
    public Frame baseline() {
        List<FlamegraphRecord> records = BASELINE_INVOCATION.get();
        lastBuiltFrame = buildFrameTree(records);
        return lastBuiltFrame;
    }

    /**
     * Optimized: Java-side frame resolution with cached frames, thread and weight grouping.
     */
    @Benchmark
    public Frame optimized() {
        List<FlamegraphRecord> records = OPTIMIZED_INVOCATION.get();
        lastBuiltFrame = buildFrameTree(records);
        return lastBuiltFrame;
    }

    private static Frame buildFrameTree(List<FlamegraphRecord> records) {
        FrameBuilder builder = new FrameBuilder(false, false, false, null);
        for (FlamegraphRecord record : records) {
            builder.onRecord(record);
        }
        return builder.build();
    }

    /**
     * Run from jmh-tests directory:
     * <pre>
     * java -cp target/benchmarks.jar pbouda.jeffrey.jmh.flamegraph.ByThreadAndWeightFlamegraphBenchmark
     * </pre>
     */
    static void main() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(FlamegraphByThreadAndWeightBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }
}
