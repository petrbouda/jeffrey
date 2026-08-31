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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.jfr.events.test.JfrRecordings;
import cafe.jeffrey.jfr.events.test.SpansAssert;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a whole {@code hprof.index.build} reports about itself.
 * <p>
 * Three of its phases hand their real work to {@code Executors.newVirtualThreadPerTaskExecutor()},
 * and a span lives in a {@code ScopedValue}, which a plain executor does not inherit. Everything
 * those workers do therefore left the trace behind: the phase drew as one bar, its duration counted
 * as the coordinator's own work, and the parallel decode that actually took the time was nowhere.
 * <p>
 * These tests are deliberately about the trace rather than about the index — the index has its own
 * coverage elsewhere. What is pinned here is that each phase's workers are still <em>inside</em> the
 * trace, which is the property a bare {@code executor.submit} silently loses.
 */
class HprofIndexTracingTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    private static final int ID_SIZE = 8;

    private static final String SPAN_EVENT = "jeffrey.TraceSpan";

    private static final long STRING_CLASS_NAME_ID = 0xA001L;
    private static final long FIELD_NAME_ID = 0xA002L;
    private static final long STRING_CLASS_ID = 0xC001L;
    private static final long BYTE_ARRAY_CLASS_ID = 0xC002L;
    private static final long INSTANCE_ID = 0xD001L;
    private static final long ARRAY_ID = 0xD002L;

    /**
     * A dump with one {@code java.lang.String} whose value array is present, which is the smallest
     * shape that gives every phase something to do — Pass B has an instance and an array to walk,
     * and write_string_content has a String to decode.
     */
    private static Path syntheticDumpWithAString(Path dir) throws IOException {
        byte[] value = {'h', 'i'};
        return SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(STRING_CLASS_NAME_ID, "java/lang/String")
                .string(FIELD_NAME_ID, "value")
                .loadClass(1, STRING_CLASS_ID, 0, STRING_CLASS_NAME_ID)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(STRING_CLASS_ID, 0L, 0L, ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(
                                        FIELD_NAME_ID, HprofTag.BasicType.OBJECT))
                        .simpleClassDump(BYTE_ARRAY_CLASS_ID, 0L, 0L, 0, FIELD_NAME_ID)
                        .instanceDump(INSTANCE_ID, STRING_CLASS_ID, idBytes(ARRAY_ID))
                        .primitiveArrayDump(ARRAY_ID, HprofTag.BasicType.BYTE, value, value.length)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, INSTANCE_ID))
                .heapDumpEnd()
                .writeTo(dir, "traced.hprof");
    }

    private static byte[] idBytes(long id) {
        return ByteBuffer.allocate(ID_SIZE).putLong(id).array();
    }

    private static List<RecordedEvent> recordIndexBuild(Path dir) throws IOException {
        Path hprof = syntheticDumpWithAString(dir);
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        return JfrRecordings.all(Set.of(SPAN_EVENT), () -> {
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void keepsEveryFannedOutPhaseInsideTheTrace(@TempDir Path dir) throws Exception {
        List<RecordedEvent> events = recordIndexBuild(dir);

        // hasNoUntracedSpans is the assertion that actually guards the regression. Dropping the fork
        // does not stop a span being recorded -- it records one with traceId=0, which the derivation
        // discards, so a count-based check stays green while the trace loses the work.
        SpansAssert.assertThat(events)
                .hasNoUntracedSpans()
                .hasSpan("walk_pass_b_worker").nestedUnder("walk_pass_b").and()
                .hasSpan("write_string_content_worker").nestedUnder("write_string_content").and()
                .hasSpan("walk_pass_b").nestedUnder("hprof.index.build").and()
                .hasSpan("write_string_content").nestedUnder("hprof.index.build");
    }

    @Test
    void namesWorkersAfterTheOperationRatherThanTheWorker(@TempDir Path dir) throws Exception {
        Map<String, Long> byName = SpansAssert.assertThat(recordIndexBuild(dir)).spanNameCardinality();

        // Every worker of a phase shares one name on purpose: a name carrying a worker index would
        // put one string per worker into JFR's per-chunk pool -- on a count that varies with the
        // machine's CPUs -- to say something the spans' own durations already say. Counting distinct
        // names under each prefix pins that regardless of how many workers this machine ran, so a
        // future change to "walk_pass_b_worker_3" style names fails here rather than quietly
        // inflating every recording.
        for (String prefix : List.of("walk_pass_b_worker", "write_string_content_worker")) {
            long distinctNames = byName.keySet().stream()
                    .filter(name -> name.startsWith(prefix))
                    .count();
            assertEquals(1L, distinctNames,
                    "one name for every worker of " + prefix + ", recorded: " + byName);
        }
    }
}
