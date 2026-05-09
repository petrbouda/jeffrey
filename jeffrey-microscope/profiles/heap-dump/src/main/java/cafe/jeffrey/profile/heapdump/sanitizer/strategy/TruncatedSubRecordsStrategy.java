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
import java.util.ArrayList;
import java.util.List;

/**
 * RECORD strategy: a HEAP_DUMP / HEAP_DUMP_SEGMENT whose declared body length
 * fits in the file but the sub-records inside don't fill the declared body
 * (truncation occurs partway through). Patches the header length to the
 * last-complete-sub-record offset and truncates the file there.
 *
 * <p>Treated as terminal: once a segment's sub-records are corrupted we don't
 * trust whatever follows.
 */
public final class TruncatedSubRecordsStrategy implements RepairStrategy {

    private static final int RECORD_HEADER_SIZE = 9;
    private static final int LENGTH_FIELD_OFFSET = 5;

    @Override
    public String id() {
        return "truncated-sub-records";
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
        long bodyLength = header.unsignedBodyLength();
        long bodyStart = header.fileOffset() + RECORD_HEADER_SIZE;
        long availableBody = ctx.fileSize() - bodyStart;

        // Other strategies handle these — bail out early to keep matching exclusive.
        if (bodyLength == 0 || bodyLength > availableBody) {
            return StrategyOutcome.notApplicable();
        }

        SubRecordScanner.ScanResult scan = SubRecordScanner.scanFromChannel(
                ctx.channel(), bodyStart, bodyLength, ctx.idSize());

        if (!scan.truncated() || scan.validBytes() >= bodyLength) {
            return StrategyOutcome.notApplicable();
        }

        long validBytes = scan.validBytes();
        long patchedEnd = bodyStart + validBytes;

        List<HprofRepair> repairs = new ArrayList<>(2);
        repairs.add(new HprofRepair.PatchRecordLength(
                header.fileOffset() + LENGTH_FIELD_OFFSET, validBytes));
        if (patchedEnd < ctx.fileSize()) {
            repairs.add(new HprofRepair.TruncateFile(patchedEnd));
        }

        return new StrategyOutcome.Applied(
                repairs,
                patchedEnd,
                true,
                scan.subRecordCount(),
                "Truncated sub-records trimmed: " + validBytes + " of " + bodyLength
                        + " bytes valid (recovered " + scan.subRecordCount() + " sub-records)");
    }
}
