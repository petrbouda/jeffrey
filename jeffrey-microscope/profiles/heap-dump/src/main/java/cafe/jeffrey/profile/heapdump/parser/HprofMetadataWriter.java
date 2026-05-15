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

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.List;

import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableInt;

/**
 * Phase 8 — writes the {@code dump_metadata} row and persists buffered
 * {@link ParseWarning}s to the {@code parse_warning} table.
 */
public final class HprofMetadataWriter {

    private HprofMetadataWriter() {
    }

    /**
     * Writes the one {@code dump_metadata} row that downstream tooling reads
     * to determine schema version, dump origin, parse health, and the JVM
     * pointer-compression mode the parser assumed when computing shallow sizes.
     *
     * <p>{@code instanceCount} / {@code classCount} / {@code gcRootCount} /
     * {@code outboundRefCount} are kept as parameters so this writer's contract
     * names every metric it could record; the table currently persists only the
     * totals already on the file/walk, but the call site stays honest about the
     * data it composes.
     */
    public static void writeMetadata(
            HeapDumpDatabaseClient client, HprofMappedFile file, Clock clock,
            TopLevelData top, long totalRecordCount,
            long instanceCount, long classCount, long gcRootCount, long outboundRefCount,
            long warningCount, boolean truncated, boolean compressedOops,
            String parserVersion) throws IOException {
        long mtimeMs = Files.getLastModifiedTime(file.path()).toMillis();
        client.withAppender(HeapDumpStatement.APPEND_DUMP_METADATA, "dump_metadata", app -> {
            app.beginRow();
            app.append(file.path().toAbsolutePath().toString());
            app.append(file.size());
            app.append(mtimeMs);
            app.append(file.header().idSize());
            app.append(file.header().version());
            app.append(file.header().timestampMs());
            app.append(file.size()); // bytes_parsed: best-effort = file size
            app.append(totalRecordCount);
            app.append(warningCount);
            app.append(truncated);
            app.append(parserVersion);
            app.append(clock.instant().toEpochMilli());
            app.append(compressedOops);
            app.endRow();
            return 1L;
        });
    }

    public static void writeWarnings(HeapDumpDatabaseClient client, List<ParseWarning> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        client.withAppender(HeapDumpStatement.APPEND_PARSE_WARNING, "parse_warning", app -> {
            long rows = 0;
            for (ParseWarning w : warnings) {
                app.beginRow();
                app.append(w.fileOffset());
                appendNullableInt(app, w.recordKind() == null ? null : w.recordKind());
                app.append((byte) w.severity().ordinal());
                app.append(w.message());
                app.endRow();
                rows++;
            }
            return rows;
        });
    }
}
