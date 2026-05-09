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

import java.util.List;

/**
 * RECORD strategy: a non-heap-dump top-level record (UTF8, LOAD_CLASS, FRAME,
 * TRACE, …) whose body extends past EOF. Truncates the file at the start of the
 * mangled record. Always terminal.
 */
public final class TruncatedNonHeapRecordStrategy implements RepairStrategy {

    private static final int RECORD_HEADER_SIZE = 9;

    @Override
    public String id() {
        return "truncated-non-heap-record";
    }

    @Override
    public Phase phase() {
        return Phase.RECORD;
    }

    @Override
    public StrategyOutcome examine(ScanContext ctx) {
        HprofRecordReader.RecordHeader header = ctx.header();
        if (header == null) {
            return StrategyOutcome.notApplicable();
        }
        HprofRecordTag tag = HprofRecordTag.fromByte(header.tag());
        // Heap-dump segment cases are owned by other strategies; same for END marker.
        if (tag != null && (tag.isHeapDumpData() || tag == HprofRecordTag.HEAP_DUMP_END)) {
            return StrategyOutcome.notApplicable();
        }

        long bodyLength = header.unsignedBodyLength();
        long bodyEnd = header.fileOffset() + RECORD_HEADER_SIZE + bodyLength;
        if (bodyEnd <= ctx.fileSize()) {
            return StrategyOutcome.notApplicable();
        }

        return new StrategyOutcome.Applied(
                List.of(new HprofRepair.TruncateFile(header.fileOffset())),
                header.fileOffset(),
                true,
                0,
                "Truncated non-heap record discarded at offset " + header.fileOffset()
                        + " (declared " + bodyLength + " bytes, only "
                        + (ctx.fileSize() - header.fileOffset() - RECORD_HEADER_SIZE) + " available)");
    }
}
