/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
