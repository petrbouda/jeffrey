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

package cafe.jeffrey.profile.heapdump.sanitizer.strategy;

import cafe.jeffrey.profile.heapdump.sanitizer.HprofRecordReader;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofRecordTag;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofRepair;
import cafe.jeffrey.profile.heapdump.sanitizer.SubRecordScanner;

import java.io.IOException;
import java.util.List;

/**
 * RECORD strategy: a HEAP_DUMP / HEAP_DUMP_SEGMENT whose declared body length is
 * zero, but actual sub-record bytes follow. This happens when the JVM writes the
 * record header but is killed before patching the length back. The strategy
 * rescans the sub-records to learn the real length and emits a length patch.
 *
 * <p>This patch is safe in-place even mid-file: the bytes after the header are
 * already part of this segment in reality — only the header lies.
 */
public final class ZeroLengthSegmentStrategy implements RepairStrategy {

    private static final int RECORD_HEADER_SIZE = 9;
    private static final int LENGTH_FIELD_OFFSET = 5; // tag(1) + timestamp(4)

    @Override
    public String id() {
        return "zero-length-segment";
    }

    @Override
    public Phase phase() {
        return Phase.RECORD;
    }

    @Override
    public StrategyOutcome examine(ScanContext ctx) throws IOException {
        HprofRecordReader.RecordHeader header = ctx.header();
        if (header == null) {
            return StrategyOutcome.notApplicable();
        }
        HprofRecordTag tag = HprofRecordTag.fromByte(header.tag());
        if (tag == null || !tag.isHeapDumpData()) {
            return StrategyOutcome.notApplicable();
        }
        if (header.unsignedBodyLength() != 0) {
            return StrategyOutcome.notApplicable();
        }

        long bodyStart = header.fileOffset() + RECORD_HEADER_SIZE;
        long availableBody = ctx.fileSize() - bodyStart;
        if (availableBody <= 0) {
            // Nothing to recover; nothing to do.
            return StrategyOutcome.notApplicable();
        }

        SubRecordScanner.ScanResult scan = SubRecordScanner.scanFromChannel(
                ctx.channel(), bodyStart, availableBody, ctx.idSize());

        long validBytes = scan.validBytes();
        if (validBytes <= 0) {
            return StrategyOutcome.notApplicable();
        }

        return new StrategyOutcome.Applied(
                List.of(new HprofRepair.PatchRecordLength(
                        header.fileOffset() + LENGTH_FIELD_OFFSET, validBytes)),
                bodyStart + validBytes,
                false,
                scan.subRecordCount(),
                "Zero-length segment rescanned: recovered " + scan.subRecordCount()
                        + " sub-records (" + validBytes + " bytes)");
    }
}
