/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import org.apache.arrow.c.ArrowArrayStream;
import org.apache.arrow.c.Data;
import org.apache.arrow.c.jni.JniLoader;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Try-once probe and warmup of the Apache Arrow runtime. Arrow needs three pieces to work:
 * an allocation manager (unsafe-based) for off-heap vectors, the C Data Interface
 * JNI library (bundled per-platform in {@code arrow-c-data}), and reflective access
 * to {@code java.nio.DirectByteBuffer} for C struct manipulation, which requires the
 * {@code --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED}
 * JVM argument. The Arrow path is the only writer for the {@code events} table, so a
 * failed probe is fatal: {@link #ensureAvailable()} throws at provider/writer
 * construction instead of letting the ingestion fail mid-parse.
 *
 * <p>The probe doubles as a lightweight warmup: it allocates a one-row
 * {@link VectorSchemaRoot} with every vector type used by the events schema and exports it
 * through the same C Data Interface path as a real batch, so Arrow's one-time costs (native
 * library load, core vector/allocator class initialization) are paid once at application
 * startup instead of inside the first profile's initialization.
 */
public final class ArrowRuntimeSupport {

    private static final long PROBE_ALLOCATOR_LIMIT_BYTES = 1024 * 1024;

    private static final String UTC_TIMEZONE = "UTC";

    private static final String WARMUP_VARCHAR_COLUMN = "warmup_varchar";
    private static final String WARMUP_TIMESTAMP_COLUMN = "warmup_timestamp";
    private static final String WARMUP_BIGINT_COLUMN = "warmup_bigint";

    private static final byte[] WARMUP_VARCHAR_VALUE = "warmup".getBytes(StandardCharsets.UTF_8);

    /**
     * One column of each vector type used by the events schema
     * ({@code DuckDBArrowEventWriter#EVENTS_SCHEMA}), so the warmup initializes
     * exactly the vector classes a real batch needs.
     */
    private static final Schema WARMUP_SCHEMA = new Schema(List.of(
            Field.notNullable(WARMUP_VARCHAR_COLUMN, new ArrowType.Utf8()),
            Field.notNullable(WARMUP_TIMESTAMP_COLUMN, new ArrowType.Timestamp(TimeUnit.MICROSECOND, UTC_TIMEZONE)),
            Field.notNullable(WARMUP_BIGINT_COLUMN, new ArrowType.Int(Long.SIZE, true))));

    private static final String UNAVAILABLE_MESSAGE =
            "Apache Arrow runtime failed to initialize — columnar event ingestion cannot start. " +
            "Run the JVM with '--add-opens=java.base/java.nio=ALL-UNNAMED' " +
            "('java -jar' picks it up automatically from the Add-Opens attribute in the fat-jar manifest) " +
            "and make sure the platform is supported by the bundled Arrow C Data native libraries " +
            "(linux x86_64/aarch64, macOS x86_64/aarch64, windows x86_64).";

    private static final Throwable PROBE_FAILURE = probe();

    private ArrowRuntimeSupport() {
    }

    /**
     * Verifies that Arrow off-heap allocation and the C Data Interface JNI library are
     * usable on the current platform.
     *
     * @throws IllegalStateException with an actionable message when the Arrow runtime
     *                               cannot be initialized
     */
    public static void ensureAvailable() {
        if (PROBE_FAILURE != null) {
            throw new IllegalStateException(UNAVAILABLE_MESSAGE, PROBE_FAILURE);
        }
    }

    private static Throwable probe() {
        try (BufferAllocator allocator = new RootAllocator(PROBE_ALLOCATOR_LIMIT_BYTES)) {
            JniLoader.get().ensureLoaded();
            // Exercises the exact operations of an exported batch: filling off-heap vectors
            // (off-heap allocation manager), allocating the C struct and wrapping it in a
            // DirectByteBuffer (fails without the java.nio add-opens), and exporting the
            // batch through the C Data Interface — which also warms these paths up.
            try (VectorSchemaRoot root = VectorSchemaRoot.create(WARMUP_SCHEMA, allocator)) {
                fillWarmupRow(root);
                try (SingleBatchArrowReader reader = new SingleBatchArrowReader(allocator, root);
                     ArrowArrayStream arrowStream = ArrowArrayStream.allocateNew(allocator)) {

                    Data.exportArrayStream(allocator, reader, arrowStream);
                }
            }
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static void fillWarmupRow(VectorSchemaRoot root) {
        VarCharVector varCharVector = (VarCharVector) root.getVector(WARMUP_VARCHAR_COLUMN);
        TimeStampMicroTZVector timestampVector = (TimeStampMicroTZVector) root.getVector(WARMUP_TIMESTAMP_COLUMN);
        BigIntVector bigIntVector = (BigIntVector) root.getVector(WARMUP_BIGINT_COLUMN);

        varCharVector.setSafe(0, WARMUP_VARCHAR_VALUE);
        timestampVector.setSafe(0, 0L);
        bigIntVector.setSafe(0, 0L);
        root.setRowCount(1);
    }
}
