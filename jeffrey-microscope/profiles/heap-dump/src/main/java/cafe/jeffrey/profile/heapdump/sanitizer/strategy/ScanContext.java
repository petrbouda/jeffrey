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

import java.nio.channels.FileChannel;

/**
 * Snapshot of the scan state passed to a {@link RepairStrategy}. Fields not
 * relevant to a given phase may be {@code null}.
 *
 * @param channel        read-only file channel positioned just after the current record header
 *                       (or at {@code position} when {@code header == null})
 * @param fileSize       total size of the file in bytes
 * @param idSize         HPROF object-ID size (4 or 8)
 * @param position       absolute file offset of the current record header (BOUNDARY/RECORD)
 *                       or end-of-scan (FINALIZE)
 * @param header         the parsed top-level record header — {@code null} in BOUNDARY/FINALIZE phases
 * @param sawEndMarker   whether the scan observed a HEAP_DUMP_END record (only meaningful in FINALIZE)
 */
public record ScanContext(
        FileChannel channel,
        long fileSize,
        int idSize,
        long position,
        HprofRecordReader.RecordHeader header,
        boolean sawEndMarker) {

    public ScanContext {
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must be non-negative: fileSize=" + fileSize);
        }
        if (idSize != 4 && idSize != 8) {
            throw new IllegalArgumentException("idSize must be 4 or 8: idSize=" + idSize);
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must be non-negative: position=" + position);
        }
    }

    /**
     * Bytes remaining in the file from the current header's body start.
     * Only meaningful when {@code header} is non-null.
     */
    public long availableBody() {
        if (header == null) {
            return fileSize - position;
        }
        return fileSize - (header.fileOffset() + 9);
    }
}
