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

import cafe.jeffrey.profile.heapdump.sanitizer.strategy.MissingEndMarkerStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.OverflowedSegmentStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.RepairStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.ScanContext;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.StrategyOutcome;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.TruncatedHeaderStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.TruncatedNonHeapRecordStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.TruncatedSubRecordsStrategy;
import cafe.jeffrey.profile.heapdump.sanitizer.strategy.ZeroLengthSegmentStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks an HPROF file once and consults the registered {@link RepairStrategy}
 * implementations to build an {@link HprofRepairPlan}. Pure read-only — never
 * mutates the file.
 */
public final class HprofRepairPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(HprofRepairPlanner.class);

    private static final int RECORD_HEADER_SIZE = 9;

    private static final List<RepairStrategy> BOUNDARY_STRATEGIES = List.of(
            new TruncatedHeaderStrategy()
    );

    private static final List<RepairStrategy> RECORD_STRATEGIES = List.of(
            new ZeroLengthSegmentStrategy(),
            new OverflowedSegmentStrategy(),
            new TruncatedSubRecordsStrategy(),
            new TruncatedNonHeapRecordStrategy()
    );

    private static final List<RepairStrategy> FINALIZE_STRATEGIES = List.of(
            new MissingEndMarkerStrategy()
    );

    private HprofRepairPlanner() {
    }

    public static HprofRepairPlan plan(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return plan(channel);
        }
    }

    static HprofRepairPlan plan(FileChannel channel) throws IOException {
        long fileSize = channel.size();
        if (fileSize == 0) {
            throw new IOException("HPROF file is empty");
        }

        // Parse header up front to learn idSize and the offset of the first record.
        int headerReadSize = (int) Math.min(256, fileSize);
        ByteBuffer headerBuf = ByteBuffer.allocate(headerReadSize);
        headerBuf.order(ByteOrder.BIG_ENDIAN);
        channel.position(0);
        readFully(channel, headerBuf);
        headerBuf.flip();

        HprofHeader header = HprofHeaderParser.parse(headerBuf);
        int idSize = header.idSize();
        long position = header.headerSize();

        ByteBuffer recordHeaderBuf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        recordHeaderBuf.order(ByteOrder.BIG_ENDIAN);

        List<HprofRepair> repairs = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();

        boolean hadZeroLengthSegments = false;
        boolean hadOverflowedLengths = false;
        boolean hadTruncatedSubRecords = false;
        boolean hadMissingEndMarker = false;
        boolean wasTruncated = false;
        int zeroLengthSegmentsFixed = 0;
        int totalRecordsProcessed = 0;
        long estimatedObjectsRecovered = 0;
        boolean sawEndMarker = false;
        boolean stopped = false;

        recordLoop:
        while (position < fileSize) {
            // BOUNDARY phase — checked before attempting to read a header.
            ScanContext boundaryCtx = new ScanContext(channel, fileSize, idSize, position, null, false);
            StrategyOutcome boundaryOutcome = consult(BOUNDARY_STRATEGIES, boundaryCtx);
            if (boundaryOutcome instanceof StrategyOutcome.Applied applied) {
                wasTruncated = true;
                recordOutcome(applied, repairs, descriptions);
                if (applied.terminal()) {
                    stopped = true;
                    break;
                }
                position = applied.nextPosition();
                continue;
            }

            // Read the next top-level record header.
            channel.position(position);
            HprofRecordReader.RecordHeader recHeader = HprofRecordReader.readHeader(channel, recordHeaderBuf);
            if (recHeader == null) {
                break;
            }
            totalRecordsProcessed++;

            HprofRecordTag tag = HprofRecordTag.fromByte(recHeader.tag());
            if (tag == HprofRecordTag.HEAP_DUMP_END) {
                sawEndMarker = true;
                position = recHeader.fileOffset() + RECORD_HEADER_SIZE + recHeader.unsignedBodyLength();
                continue;
            }

            // RECORD phase — consult each strategy in order.
            ScanContext recordCtx = new ScanContext(channel, fileSize, idSize, position, recHeader, false);
            StrategyOutcome recordOutcome = consult(RECORD_STRATEGIES, recordCtx);
            if (recordOutcome instanceof StrategyOutcome.Applied applied) {
                switch (applied.repairs().get(0)) {
                    case HprofRepair.PatchRecordLength ignored -> {
                        if (recHeader.unsignedBodyLength() == 0) {
                            hadZeroLengthSegments = true;
                            zeroLengthSegmentsFixed++;
                        } else if (recHeader.unsignedBodyLength() > recordCtx.availableBody()) {
                            hadOverflowedLengths = true;
                            wasTruncated = true;
                        } else {
                            hadTruncatedSubRecords = true;
                            wasTruncated = true;
                        }
                    }
                    case HprofRepair.TruncateFile ignored -> wasTruncated = true;
                    case HprofRepair.AppendEndMarker ignored -> {
                        // not expected during RECORD phase
                    }
                }
                estimatedObjectsRecovered += applied.objectsRecovered();
                recordOutcome(applied, repairs, descriptions);

                if (applied.terminal()) {
                    stopped = true;
                    break;
                }
                position = applied.nextPosition();
                continue;
            }

            // No corruption — just advance past the record body.
            long bodyEnd = recHeader.fileOffset() + RECORD_HEADER_SIZE + recHeader.unsignedBodyLength();
            if (bodyEnd > fileSize) {
                // Defensive: every strategy declined but the record overruns. Stop here.
                wasTruncated = true;
                LOG.warn("Record overruns EOF and no strategy claimed it: offset={} declaredBodyLength={}",
                        recHeader.fileOffset(), recHeader.unsignedBodyLength());
                break recordLoop;
            }
            position = bodyEnd;
        }

        // FINALIZE phase.
        long endPosition = stopped
                ? (repairs.isEmpty() ? position : tailAfterRepairs(position, repairs))
                : Math.min(position, fileSize);
        ScanContext finalizeCtx = new ScanContext(channel, fileSize, idSize, endPosition, null, sawEndMarker);
        for (RepairStrategy s : FINALIZE_STRATEGIES) {
            StrategyOutcome outcome = s.examine(finalizeCtx);
            if (outcome instanceof StrategyOutcome.Applied applied) {
                hadMissingEndMarker = true;
                recordOutcome(applied, repairs, descriptions);
            }
        }

        boolean modified = !repairs.isEmpty();
        long bytesAfterRepairs = projectFinalSize(fileSize, repairs);
        String summary = modified
                ? "Repairs applied: " + String.join("; ", descriptions)
                : "No repairs needed";

        SanitizeResult result = new SanitizeResult(
                modified,
                hadZeroLengthSegments,
                wasTruncated,
                hadMissingEndMarker,
                hadTruncatedSubRecords,
                hadOverflowedLengths,
                zeroLengthSegmentsFixed,
                totalRecordsProcessed,
                fileSize,
                bytesAfterRepairs,
                estimatedObjectsRecovered,
                summary);

        return new HprofRepairPlan(repairs, result);
    }

    private static StrategyOutcome consult(List<RepairStrategy> strategies, ScanContext ctx) throws IOException {
        for (RepairStrategy s : strategies) {
            StrategyOutcome outcome = s.examine(ctx);
            if (outcome instanceof StrategyOutcome.Applied) {
                return outcome;
            }
        }
        return StrategyOutcome.notApplicable();
    }

    private static void recordOutcome(StrategyOutcome.Applied applied, List<HprofRepair> repairs, List<String> descriptions) {
        repairs.addAll(applied.repairs());
        descriptions.add(applied.description());
    }

    private static long tailAfterRepairs(long fallback, List<HprofRepair> repairs) {
        for (int i = repairs.size() - 1; i >= 0; i--) {
            if (repairs.get(i) instanceof HprofRepair.TruncateFile t) {
                return t.offset();
            }
        }
        return fallback;
    }

    private static long projectFinalSize(long originalSize, List<HprofRepair> repairs) {
        long size = originalSize;
        for (HprofRepair r : repairs) {
            switch (r) {
                case HprofRepair.TruncateFile t -> size = Math.min(size, t.offset());
                case HprofRepair.AppendEndMarker ignored -> size += RECORD_HEADER_SIZE;
                case HprofRepair.PatchRecordLength ignored -> {
                    // length patches don't change file size
                }
            }
        }
        return size;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                throw new IOException("Unexpected end of file while reading header");
            }
        }
    }

}
