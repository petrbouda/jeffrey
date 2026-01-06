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

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility to generate baseline hash files for benchmark verification.
 * Run this after creating new benchmarks to generate the expected baseline hashes.
 */
public class BaselineGenerator {

    private static final Path DATABASE_PATH = Path.of("jmh-tests/data/profile-data.db");
    private static final String EVENT_TYPE = "jdk.ExecutionSample";
    private static final String JDBC_URL = "jdbc:duckdb:" + DATABASE_PATH.toAbsolutePath();

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

    public static void main(String[] args) throws IOException {
        System.out.println("Generating baseline files...\n");

        // Generate SimpleFlamegraphBenchmark baseline
        generateSimpleFlamegraphBenchmarkBaseline();

        // Generate FlamegraphBenchmark baseline
        generateFlamegraphBenchmarkBaseline();

        // Generate ByThreadFlamegraphBenchmark baseline
        generateByThreadFlamegraphBenchmarkBaseline();

        // Generate ByThreadAndWeightFlamegraphBenchmark baseline
        generateByThreadAndWeightFlamegraphBenchmarkBaseline();

        System.out.println("\nAll baselines generated successfully!");
    }

    private static void generateSimpleFlamegraphBenchmarkBaseline() throws IOException {
        System.out.println("Generating SimpleFlamegraphBenchmark baseline...");

        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().simple();
        SimpleFlamegraphRecordRowMapper rowMapper = new SimpleFlamegraphRecordRowMapper(Type.EXECUTION_SAMPLE);
        List<FlamegraphRecord> records = jdbcTemplate.query(sql, QUERY_PARAMS, rowMapper);

        Frame frame = buildFrameTree(records);
        byte[] jsonBytes = FrameJsonSerializer.toJsonBytes(frame);
        String hash = hashFrame(frame);

        Path baselineDir = Path.of("jmh-tests/data/baseline/SimpleFlamegraphBenchmark");
        Files.createDirectories(baselineDir);
        Files.writeString(baselineDir.resolve("baseline-frame.murmur3"), hash);
        Files.write(baselineDir.resolve("baseline-frame.json"), jsonBytes);

        System.out.println("  Records: " + records.size());
        System.out.println("  Hash: " + hash);
        System.out.println("  Saved to: " + baselineDir);
    }

    private static void generateFlamegraphBenchmarkBaseline() throws IOException {
        System.out.println("Generating FlamegraphBenchmark baseline...");

        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().byWeight();
        FlamegraphRecordRowMapper rowMapper = new FlamegraphRecordRowMapper(Type.EXECUTION_SAMPLE);
        List<FlamegraphRecord> records = jdbcTemplate.query(sql, QUERY_PARAMS, rowMapper);

        Frame frame = buildFrameTree(records);
        byte[] jsonBytes = FrameJsonSerializer.toJsonBytes(frame);
        String hash = hashFrame(frame);

        Path baselineDir = Path.of("jmh-tests/data/baseline/FlamegraphBenchmark");
        Files.createDirectories(baselineDir);
        Files.writeString(baselineDir.resolve("baseline-frame.murmur3"), hash);
        Files.write(baselineDir.resolve("baseline-frame.json"), jsonBytes);

        System.out.println("  Records: " + records.size());
        System.out.println("  Hash: " + hash);
        System.out.println("  Saved to: " + baselineDir);
    }

    private static void generateByThreadFlamegraphBenchmarkBaseline() throws IOException {
        System.out.println("Generating ByThreadFlamegraphBenchmark baseline...");

        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().byThread();
        // byThread query doesn't include weight_entity, use the appropriate mapper
        FlamegraphRecordByThreadRowMapper rowMapper = new FlamegraphRecordByThreadRowMapper(Type.EXECUTION_SAMPLE);
        List<FlamegraphRecord> records = jdbcTemplate.query(sql, QUERY_PARAMS_WITH_THREAD, rowMapper);

        Frame frame = buildFrameTree(records);
        byte[] jsonBytes = FrameJsonSerializer.toJsonBytes(frame);
        String hash = hashFrame(frame);

        Path baselineDir = Path.of("jmh-tests/data/baseline/ByThreadFlamegraphBenchmark");
        Files.createDirectories(baselineDir);
        Files.writeString(baselineDir.resolve("baseline-frame.murmur3"), hash);
        Files.write(baselineDir.resolve("baseline-frame.json"), jsonBytes);

        System.out.println("  Records: " + records.size());
        System.out.println("  Hash: " + hash);
        System.out.println("  Saved to: " + baselineDir);
    }

    private static void generateByThreadAndWeightFlamegraphBenchmarkBaseline() throws IOException {
        System.out.println("Generating ByThreadAndWeightFlamegraphBenchmark baseline...");

        DataSource ds = new SimpleJdbcDataSource(JDBC_URL);
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(ds);
        String sql = DuckDBFlamegraphQueries.of().byThreadAndWeight();
        FlamegraphRecordWithThreadsRowMapper rowMapper = new FlamegraphRecordWithThreadsRowMapper(Type.EXECUTION_SAMPLE);
        List<FlamegraphRecord> records = jdbcTemplate.query(sql, QUERY_PARAMS_WITH_THREAD, rowMapper);

        Frame frame = buildFrameTree(records);
        byte[] jsonBytes = FrameJsonSerializer.toJsonBytes(frame);
        String hash = hashFrame(frame);

        Path baselineDir = Path.of("jmh-tests/data/baseline/ByThreadAndWeightFlamegraphBenchmark");
        Files.createDirectories(baselineDir);
        Files.writeString(baselineDir.resolve("baseline-frame.murmur3"), hash);
        Files.write(baselineDir.resolve("baseline-frame.json"), jsonBytes);

        System.out.println("  Records: " + records.size());
        System.out.println("  Hash: " + hash);
        System.out.println("  Saved to: " + baselineDir);
    }

    private static Frame buildFrameTree(List<FlamegraphRecord> records) {
        FrameBuilder builder = new FrameBuilder(false, false, false, null);
        for (FlamegraphRecord record : records) {
            builder.onRecord(record);
        }
        return builder.build();
    }

    /**
     * Computes a murmur3 hash of the entire Frame tree structure.
     * This must match the hashing logic in BenchmarkVerification.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static String hashFrame(Frame frame) {
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        hashFrameRecursive(hasher, frame);
        return hasher.hash().toString();
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void hashFrameRecursive(Hasher hasher, Frame frame) {
        // Hash frame fields
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

        // Hash children count to detect structural differences
        hasher.putInt(frame.size());

        // Recursively hash children (TreeMap ensures consistent ordering)
        for (Frame child : frame.values()) {
            hashFrameRecursive(hasher, child);
        }
    }
}
