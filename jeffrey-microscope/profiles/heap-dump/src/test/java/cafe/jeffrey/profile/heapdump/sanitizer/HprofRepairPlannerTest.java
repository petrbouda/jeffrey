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

package cafe.jeffrey.profile.heapdump.sanitizer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofRepairPlannerTest {

    @TempDir
    Path tempDir;

    @Nested
    class CleanFiles {

        @Test
        void emptyPlanWhenNothingIsBroken() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root).addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("clean.hprof"));

            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            assertTrue(plan.isClean());
            assertTrue(plan.repairs().isEmpty());
            assertFalse(plan.result().wasModified());
            // Pure read — file untouched.
            assertTrue(Files.size(file) > 0);
        }
    }

    @Nested
    class CorruptionPatterns {

        @Test
        void zeroLengthSegmentEmitsOnePatch() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addZeroLengthHeapDumpSegment(root).addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("zero.hprof"));

            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            assertEquals(1, plan.repairs().size());
            assertInstanceOf(HprofRepair.PatchRecordLength.class, plan.repairs().get(0));
            assertTrue(plan.result().hadZeroLengthSegments());
            assertEquals(1, plan.result().zeroLengthSegmentsFixed());
        }

        @Test
        void overflowedSegmentEmitsPatchAndAppend() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addOverflowedHeapDumpSegment(root, 999_999);

            Path file = builder.writeTo(tempDir.resolve("overflow.hprof"));

            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            // patch length + append end marker (no truncate because patched end == fileSize)
            List<HprofRepair> repairs = plan.repairs();
            assertEquals(2, repairs.size());
            assertInstanceOf(HprofRepair.PatchRecordLength.class, repairs.get(0));
            assertInstanceOf(HprofRepair.AppendEndMarker.class, repairs.get(1));
            assertTrue(plan.result().hadOverflowedLengths());
            assertTrue(plan.result().hadMissingEndMarker());
        }

        @Test
        void missingEndMarkerEmitsAppendOnly() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);
            // No addHeapDumpEnd()

            Path file = builder.writeTo(tempDir.resolve("no-end.hprof"));

            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            assertEquals(1, plan.repairs().size());
            assertInstanceOf(HprofRepair.AppendEndMarker.class, plan.repairs().get(0));
        }

        @Test
        void strayBytesEmitTruncateAndAppend() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);
            builder.writeRawBytes(new byte[]{0x1C, 0x00, 0x00});

            Path file = builder.writeTo(tempDir.resolve("stray.hprof"));

            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            assertEquals(2, plan.repairs().size());
            assertInstanceOf(HprofRepair.TruncateFile.class, plan.repairs().get(0));
            assertInstanceOf(HprofRepair.AppendEndMarker.class, plan.repairs().get(1));
        }
    }

    @Nested
    class PlanInvariants {

        @Test
        void truncateAlwaysPrecedesAppendInOps() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);
            builder.writeRawBytes(new byte[]{0x1C, 0x00, 0x00});

            Path file = builder.writeTo(tempDir.resolve("ordering.hprof"));
            HprofRepairPlan plan = HprofRepairPlanner.plan(file);

            int truncIdx = -1;
            int appendIdx = -1;
            for (int i = 0; i < plan.repairs().size(); i++) {
                if (plan.repairs().get(i) instanceof HprofRepair.TruncateFile) {
                    truncIdx = i;
                }
                if (plan.repairs().get(i) instanceof HprofRepair.AppendEndMarker) {
                    appendIdx = i;
                }
            }
            assertTrue(truncIdx >= 0 && appendIdx >= 0);
            assertTrue(truncIdx < appendIdx,
                    "TruncateFile must precede AppendEndMarker (index " + truncIdx + " vs " + appendIdx + ")");
        }
    }
}
