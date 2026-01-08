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

package pbouda.jeffrey.jmh.flamegraph.utils;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameBuilder;
import pbouda.jeffrey.jmh.flamegraph.mapper.FlamegraphRecordByThreadRowMapper;
import pbouda.jeffrey.jmh.flamegraph.mapper.SimpleFlamegraphRecordRowMapper;
import pbouda.jeffrey.provider.profile.model.FlamegraphRecord;
import pbouda.jeffrey.provider.profile.query.DuckDBFlamegraphQueries;
import pbouda.jeffrey.provider.profile.query.FlamegraphRecordRowMapper;
import pbouda.jeffrey.provider.profile.query.FlamegraphRecordWithThreadsRowMapper;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.persistence.SimpleJdbcDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Utility to generate baseline hash files for benchmark verification.
 * Run this after creating new benchmarks to generate the expected baseline hashes.
 */
public class BaselineGenerator {

    private static final Path DATABASE_PATH = Path.of("jmh-tests/data/profile-data.db");
    private static final String JDBC_URL = "jdbc:duckdb:" + DATABASE_PATH.toAbsolutePath();
    private static final String EVENT_TYPE = "jdk.ExecutionSample";
    private static final Path BASELINE_DIR = Path.of("jmh-tests/data/baseline");

    private static final MapSqlParameterSource QUERY_PARAMS = new MapSqlParameterSource()
            .addValue("event_type", EVENT_TYPE)
            .addValue("from_time", null)
            .addValue("to_time", null)
            .addValue("stacktrace_types", null)
            .addValue("included_tags", null)
            .addValue("excluded_tags", null);

    private static final MapSqlParameterSource QUERY_PARAMS_WITH_THREAD = new MapSqlParameterSource()
            .addValue("event_type", EVENT_TYPE)
            .addValue("from_time", null)
            .addValue("to_time", null)
            .addValue("java_thread_id", null)
            .addValue("os_thread_id", null)
            .addValue("stacktrace_types", null)
            .addValue("included_tags", null)
            .addValue("excluded_tags", null);

    private record BenchmarkConfig(
            String name,
            Supplier<String> sqlSupplier,
            RowMapper<FlamegraphRecord> rowMapper,
            MapSqlParameterSource params
    ) {}

    private static final List<BenchmarkConfig> BENCHMARKS = List.of(
            new BenchmarkConfig(
                    "SimpleFlamegraphBenchmark",
                    () -> DuckDBFlamegraphQueries.of().simple(),
                    new SimpleFlamegraphRecordRowMapper(Type.EXECUTION_SAMPLE),
                    QUERY_PARAMS),
            new BenchmarkConfig(
                    "FlamegraphBenchmark",
                    () -> DuckDBFlamegraphQueries.of().byWeight(),
                    new FlamegraphRecordRowMapper(Type.EXECUTION_SAMPLE),
                    QUERY_PARAMS),
            new BenchmarkConfig(
                    "ByThreadFlamegraphBenchmark",
                    () -> DuckDBFlamegraphQueries.of().byThread(),
                    new FlamegraphRecordByThreadRowMapper(Type.EXECUTION_SAMPLE),
                    QUERY_PARAMS_WITH_THREAD),
            new BenchmarkConfig(
                    "ByThreadAndWeightFlamegraphBenchmark",
                    () -> DuckDBFlamegraphQueries.of().byThreadAndWeight(),
                    new FlamegraphRecordWithThreadsRowMapper(Type.EXECUTION_SAMPLE),
                    QUERY_PARAMS_WITH_THREAD)
    );

    public static void main(String[] args) throws IOException {
        System.out.println("Generating baseline files...\n");

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(new SimpleJdbcDataSource(JDBC_URL));

        for (BenchmarkConfig config : BENCHMARKS) {
            generateBaseline(jdbcTemplate, config);
        }

        System.out.println("\nAll baselines generated successfully!");
    }

    private static void generateBaseline(NamedParameterJdbcTemplate jdbcTemplate, BenchmarkConfig config) throws IOException {
        System.out.println("Generating " + config.name() + " baseline...");

        List<FlamegraphRecord> records = jdbcTemplate.query(config.sqlSupplier().get(), config.params(), config.rowMapper());
        Frame frame = buildFrameTree(records);

        Path baselineDir = BASELINE_DIR.resolve(config.name());
        Files.createDirectories(baselineDir);
        Files.writeString(baselineDir.resolve("baseline-frame.murmur3"), hashFrame(frame));
        Files.write(baselineDir.resolve("baseline-frame.json"), FrameJsonSerializer.toJsonBytes(frame));

        System.out.println("  Records: " + records.size());
        System.out.println("  Saved to: " + baselineDir);
    }

    private static Frame buildFrameTree(List<FlamegraphRecord> records) {
        FrameBuilder builder = new FrameBuilder(false, false, false, null);
        for (FlamegraphRecord record : records) {
            builder.onRecord(record);
        }
        return builder.build();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static String hashFrame(Frame frame) {
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        hashFrameRecursive(hasher, frame);
        return hasher.hash().toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void hashFrameRecursive(Hasher hasher, Frame frame) {
        if (frame.methodName() != null) {
            hasher.putString(frame.methodName(), StandardCharsets.UTF_8);
        }
        hasher.putInt(frame.lineNumber());
        hasher.putInt(frame.bci());
        hasher.putLong(frame.totalSamples());
        hasher.putLong(frame.totalWeight());
        hasher.putLong(frame.selfSamples());
        hasher.putLong(frame.selfWeight());
        hasher.putLong(frame.c1Samples());
        hasher.putLong(frame.interpretedSamples());
        hasher.putLong(frame.jitCompiledSamples());
        hasher.putLong(frame.inlinedSamples());
        hasher.putInt(frame.size());

        for (Frame child : frame.values()) {
            hashFrameRecursive(hasher, child);
        }
    }
}
