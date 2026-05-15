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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.JavaStringDecoder;
import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetSink;
import cafe.jeffrey.profile.heapdump.parser.parquet.ParquetStaging;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import org.duckdb.DuckDBAppender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Phase 7 — decodes every {@code java.lang.String} instance and writes one row
 * per String to the {@code string_content} table. Content is set to
 * {@code NULL} when its decoded length exceeds {@code threshold} (so OQL
 * string predicates can detect uncovered Strings via
 * {@code WHERE sc.content IS NULL} for the opt-in scan-large-Strings fallback
 * path).
 *
 * <p>Returns the number of rows written. A {@code threshold < 0} disables the
 * cap (every String is materialised in full).
 */
public final class HprofStringContentWriter {

    // HPROF stores class names with slash separators; "java/lang/String" is the
    // canonical String type name as it appears in the string pool.
    private static final String STRING_CLASS_HPROF_NAME = "java/lang/String";

    private static final String STRING_CONTENT_TABLE = "string_content";

    private static final String STRING_CONTENT_STAGING_DDL =
            "instance_id BIGINT, content_length INTEGER, content VARCHAR";

    private static final String INSTANCES_PER_CLASS_ID_SQL =
            "SELECT instance_id, file_offset FROM instance WHERE class_id = ?";

    private HprofStringContentWriter() {
    }

    public static long write(
            HeapDumpDatabaseClient client, HprofMappedFile file, TopLevelData top,
            ClassDumpIndex classes, Map<Long, PrimitiveArrayInfo> primArrInfo,
            int idSize, int threshold,
            Path stagingDir, int requestedWorkers) throws SQLException, IOException {

        Long stringClassId = findClassIdByHprofName(top);
        if (stringClassId == null) {
            return 0;
        }
        HprofRecord.ClassDump cd = classes.byId().get(stringClassId);
        if (cd == null) {
            return 0;
        }

        int valueOffset = -1;
        int coderOffset = -1;
        long[] nameIds = cd.instanceFieldNameIds();
        int[] types = cd.instanceFieldTypes();
        int offset = 0;
        for (int i = 0; i < types.length; i++) {
            byte[] nameBytes = top.stringPool.get(nameIds[i]);
            String name = nameBytes == null
                    ? ""
                    : new String(nameBytes, StandardCharsets.UTF_8);
            int type = types[i];
            int size = HprofTypeSize.sizeOf(type, idSize);
            if ("value".equals(name) && type == HprofTag.BasicType.OBJECT) {
                valueOffset = offset;
            } else if ("coder".equals(name) && type == HprofTag.BasicType.BYTE) {
                coderOffset = offset;
            }
            offset += size;
        }
        if (valueOffset < 0) {
            return 0;
        }

        // Materialise every String instance's (id, file_offset) pair into compact
        // primitive arrays first. The decode + write phase then runs in parallel
        // virtual threads, each owning its own slice and parquet shard. Reading
        // the metadata up-front isolates DuckDB I/O on the main thread and lets
        // the per-row decode (which dominates on real heaps) parallelise cleanly.
        long[][] stringRows = loadStringInstanceRows(client, stringClassId);
        long[] instanceIds = stringRows[0];
        long[] fileOffsets = stringRows[1];
        int total = instanceIds.length;
        if (total == 0) {
            return 0L;
        }

        int n = Math.max(1, Math.min(requestedWorkers, total));
        int[] starts = new int[n];
        int[] ends = new int[n];
        int chunk = total / n;
        int extra = total - chunk * n;
        int cursor = 0;
        for (int w = 0; w < n; w++) {
            int size = chunk + (w < extra ? 1 : 0);
            starts[w] = cursor;
            ends[w] = cursor + size;
            cursor += size;
        }

        final int finalValueOffset = valueOffset;
        final int finalCoderOffset = coderOffset;
        Map<Long, PrimitiveArrayInfo> arrayInfoByArrayId = primArrInfo;

        long emitted;
        try (ParquetStaging staging = ParquetStaging.open(stagingDir)) {
            staging.prepareTable(STRING_CONTENT_TABLE);

            List<Future<Long>> futures = new ArrayList<>(n);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int w = 0; w < n; w++) {
                    int start = starts[w];
                    int end = ends[w];
                    Path outputPath = staging.partFile(STRING_CONTENT_TABLE, w);
                    futures.add(executor.submit(() -> runWorker(
                            file, instanceIds, fileOffsets, start, end,
                            finalValueOffset, finalCoderOffset, idSize,
                            arrayInfoByArrayId, threshold, outputPath)));
                }
            }

            emitted = 0L;
            for (Future<Long> f : futures) {
                emitted += FutureJoin.unwrap(f);
            }

            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_STRING_CONTENT, STRING_CONTENT_TABLE);
        }
        return emitted;
    }

    private static long[][] loadStringInstanceRows(HeapDumpDatabaseClient client, long stringClassId)
            throws SQLException {
        long count = client.queryLong(HeapDumpStatement.TOTAL_INSTANCE_COUNT,
                "SELECT COUNT(*) FROM instance WHERE class_id = ?", stringClassId);
        int total = (int) Math.min(count, Integer.MAX_VALUE);
        long[] instanceIds = new long[total];
        long[] fileOffsets = new long[total];
        int[] cursor = {0};
        try (PreparedStatement ps = client.connection().prepareStatement(INSTANCES_PER_CLASS_ID_SQL)) {
            ps.setLong(1, stringClassId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && cursor[0] < total) {
                    instanceIds[cursor[0]] = rs.getLong(1);
                    fileOffsets[cursor[0]] = rs.getLong(2);
                    cursor[0]++;
                }
            }
        }
        int filled = cursor[0];
        if (filled < total) {
            long[] trimmedIds = new long[filled];
            long[] trimmedOffsets = new long[filled];
            System.arraycopy(instanceIds, 0, trimmedIds, 0, filled);
            System.arraycopy(fileOffsets, 0, trimmedOffsets, 0, filled);
            return new long[][]{trimmedIds, trimmedOffsets};
        }
        return new long[][]{instanceIds, fileOffsets};
    }

    private static long runWorker(
            HprofMappedFile file, long[] instanceIds, long[] fileOffsets, int start, int end,
            int valueOffset, int coderOffset, int idSize,
            Map<Long, PrimitiveArrayInfo> arrayInfoByArrayId,
            int threshold, Path outputPath) {
        long emitted = 0L;
        try (ParquetSink sink = ParquetSink.open(
                Map.of(STRING_CONTENT_TABLE, STRING_CONTENT_STAGING_DDL),
                Map.of(STRING_CONTENT_TABLE, outputPath))) {
            DuckDBAppender app = sink.appender(STRING_CONTENT_TABLE);
            for (int i = start; i < end; i++) {
                long instanceId = instanceIds[i];
                long instOffset = fileOffsets[i];
                long fieldBlockStart = instOffset + 2L * idSize + 8L;

                long valueRef = file.readId(fieldBlockStart + valueOffset);
                Byte coder = coderOffset >= 0
                        ? file.readByte(fieldBlockStart + coderOffset)
                        : null;

                String content;
                if (valueRef == 0L) {
                    content = "";
                } else {
                    PrimitiveArrayInfo info = arrayInfoByArrayId.get(valueRef);
                    if (info == null) {
                        continue;
                    }
                    content = decodeStringForIndex(file, info, coder, idSize);
                    if (content == null) {
                        continue;
                    }
                }

                int len = content.length();
                app.beginRow();
                app.append(instanceId);
                app.append(len);
                if (threshold >= 0 && len > threshold) {
                    app.appendNull();
                } else {
                    app.append(content);
                }
                app.endRow();
                emitted++;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return emitted;
    }

    private static String decodeStringForIndex(
            HprofMappedFile file, PrimitiveArrayInfo info, Byte coder, int idSize) {
        int elementSize = HprofTypeSize.sizeOf(info.elementType(), idSize);
        if (elementSize < 0) {
            return null;
        }
        long payloadOffset = info.fileOffset() + idSize + 9L;
        long byteLengthLong = (long) info.arrayLength() * elementSize;
        if (byteLengthLong > Integer.MAX_VALUE) {
            byteLengthLong = Integer.MAX_VALUE;
        }
        byte[] bytes = file.readBytes(payloadOffset, (int) byteLengthLong);
        return JavaStringDecoder.decodeContent(bytes, info.elementType(), coder);
    }

    private static Long findClassIdByHprofName(TopLevelData top) {
        String userFacing = ClassNameFormatter.userFacing(STRING_CLASS_HPROF_NAME);
        for (HprofRecord.LoadClass lc : top.loadClassByClassId.values()) {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            if (nameBytes == null) {
                continue;
            }
            String raw = new String(nameBytes, StandardCharsets.UTF_8);
            if (STRING_CLASS_HPROF_NAME.equals(raw)
                    || userFacing.equals(ClassNameFormatter.userFacing(raw))) {
                return lc.classId();
            }
        }
        return null;
    }
}
