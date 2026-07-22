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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import cafe.jeffrey.profile.heapdump.model.DuplicateArrayGroup;
import cafe.jeffrey.profile.heapdump.model.DuplicateDataReport;
import cafe.jeffrey.profile.heapdump.parser.FutureJoin;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

/**
 * Finds byte-identical primitive arrays — the raw storage behind buffers,
 * caches, deserialized payloads and non-deduplicated strings — and reports how
 * much heap could be reclaimed by sharing a single copy per group.
 *
 * <p>Arrays are grouped by {@code (primitiveType, length, XXHash64(content))};
 * content is read straight from the mapped {@code .hprof} one array at a time,
 * so peak memory is a single array's payload. A 64-bit content hash stands in
 * for full byte equality — with equal type and length the collision
 * probability is negligible for reporting purposes.
 *
 * <p>Arrays smaller than {@link #MIN_CONTENT_BYTES} are ignored (empty/near-empty
 * arrays are dominated by legitimately shared constants), and arrays larger
 * than {@link #MAX_HASH_BYTES} are skipped rather than partially hashed, so a
 * prefix collision can never masquerade as a duplicate; the skipped count is
 * surfaced in the report.
 *
 * <h2>Cost control</h2>
 * A byte-duplicate requires at least {@link #MIN_DUPLICATE_GROUP_SIZE} arrays
 * that share the same {@code (primitiveType, length)}. Any array whose
 * {@code (type, length)} is unique in the heap therefore can never be a
 * duplicate, so it is neither read nor hashed: a cheap SQL {@code GROUP BY ...
 * HAVING COUNT(*) >= 2} pre-pass narrows the scan to candidate buckets before
 * the expensive per-array reads. The surviving candidates are read+hashed in
 * parallel across virtual-thread workers reading purely by {@code file_offset}
 * from the shared mmap (no per-worker database connection), mirroring the
 * index-build fan-out. Reported totals ({@code totalPrimitiveArrays},
 * {@code totalArrayBytes}) are computed from a separate aggregate so they still
 * count every primitive array regardless of the candidate filter.
 */
public final class DuplicateArrayAnalyzer {

    private static final int DEFAULT_TOP_N = 50;

    /** Arrays with payloads below this size are noise (shared empty arrays etc.). */
    private static final int MIN_CONTENT_BYTES = 16;

    /** Arrays larger than this are skipped instead of partially hashed. */
    private static final int MAX_HASH_BYTES = 64 * 1024 * 1024;

    private static final long HASH_SEED = 0x9E3779B97F4A7C15L;

    private static final int SAMPLE_IDS_PER_GROUP = 3;

    private static final int PREVIEW_BYTES = 256;

    private static final int PREVIEW_MAX_CHARS = 60;

    /** {@code record_kind} discriminator for primitive arrays in the {@code instance} table. */
    private static final int RECORD_KIND_PRIMITIVE_ARRAY = 2;

    /** Minimum arrays that must share a group before it can be a duplicate. */
    private static final int MIN_DUPLICATE_GROUP_SIZE = 2;

    private static final int MIN_WORKERS = 1;

    private static final int MAX_WORKERS = 16;

    /** Candidate rows per unit of parallel work; bounds a worker's transient list. */
    private static final int BATCH_SIZE = 8192;

    /** In-flight batches per worker; caps retained batches so memory stays bounded. */
    private static final int IN_FLIGHT_BATCHES_PER_WORKER = 2;

    private static final String TOTALS_SQL =
            "SELECT COUNT(*), COALESCE(SUM(shallow_size), 0) FROM instance WHERE record_kind = "
                    + RECORD_KIND_PRIMITIVE_ARRAY;

    /**
     * Selects only arrays whose {@code (primitive_type, array_length)} bucket has
     * at least {@link #MIN_DUPLICATE_GROUP_SIZE} members — the sole rows that can
     * possibly be duplicates. {@code file_offset} is selected so the payload read
     * skips the per-array primary-key lookup.
     */
    private static final String CANDIDATE_ARRAYS_SQL = """
            WITH candidates AS (
              SELECT primitive_type, array_length
              FROM instance
              WHERE record_kind = %1$d
              GROUP BY primitive_type, array_length
              HAVING COUNT(*) >= %2$d
            )
            SELECT i.instance_id, i.array_length, i.primitive_type, i.shallow_size, i.file_offset, c.name
            FROM instance i
            JOIN candidates cand
              ON cand.primitive_type = i.primitive_type AND cand.array_length = i.array_length
            LEFT JOIN class c ON c.class_id = i.class_id
            WHERE i.record_kind = %1$d
            """.formatted(RECORD_KIND_PRIMITIVE_ARRAY, MIN_DUPLICATE_GROUP_SIZE);

    private DuplicateArrayAnalyzer() {
    }

    public static DuplicateDataReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static DuplicateDataReport analyze(HeapView view, int topN) throws SQLException {
        return analyze(view, topN, BATCH_SIZE);
    }

    /**
     * Package-private seam: {@code batchSize} controls how many candidate rows a
     * single parallel unit of work processes. Production uses {@link #BATCH_SIZE};
     * tests pass a tiny value to force the cross-batch merge path deterministically.
     */
    static DuplicateDataReport analyze(HeapView view, int topN, int batchSize) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: batchSize=" + batchSize);
        }

        long[] totals = readTotals(view);
        long totalArrays = totals[0];
        long totalArrayBytes = totals[1];

        ScanResult scan = scanCandidates(view, batchSize);
        Map<GroupKey, GroupAcc> groups = scan.groups();
        long oversizedSkipped = scan.oversizedSkipped();

        long duplicateGroups = 0;
        long duplicateArrayCount = 0;
        long potentialSavings = 0;
        List<Map.Entry<GroupKey, GroupAcc>> duplicates = new ArrayList<>();
        for (Map.Entry<GroupKey, GroupAcc> e : groups.entrySet()) {
            GroupAcc acc = e.getValue();
            if (acc.count < MIN_DUPLICATE_GROUP_SIZE) {
                continue;
            }
            duplicateGroups++;
            duplicateArrayCount += acc.count - 1;
            potentialSavings += acc.wastedBytes();
            duplicates.add(e);
        }
        duplicates.sort(Comparator.comparingLong(
                (Map.Entry<GroupKey, GroupAcc> e) -> e.getValue().wastedBytes()).reversed());

        List<DuplicateArrayGroup> topGroups = new ArrayList<>(Math.min(topN, duplicates.size()));
        for (Map.Entry<GroupKey, GroupAcc> e : duplicates.subList(0, Math.min(topN, duplicates.size()))) {
            GroupKey key = e.getKey();
            GroupAcc acc = e.getValue();
            String preview = renderPreview(view, acc.firstInstanceId, key.primitiveType());
            topGroups.add(new DuplicateArrayGroup(
                    acc.typeName == null ? "<unknown>" : acc.typeName,
                    key.arrayLength(),
                    acc.count,
                    acc.shallowSize,
                    acc.wastedBytes(),
                    preview,
                    acc.sampleIds()));
        }

        return new DuplicateDataReport(
                totalArrays,
                totalArrayBytes,
                duplicateGroups,
                duplicateArrayCount,
                potentialSavings,
                oversizedSkipped,
                topGroups);
    }

    /** Total count and shallow-size sum of every primitive array (candidate filter aside). */
    private static long[] readTotals(HeapView view) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(TOTALS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return new long[] {0L, 0L};
            }
            return new long[] {rs.getLong(1), rs.getLong(2)};
        }
    }

    /**
     * Streams candidate arrays in bounded batches and read+hashes them across
     * virtual-thread workers, merging the per-worker group maps. The main thread
     * only touches the database connection (reading candidate rows); workers read
     * payloads purely from the shared mmap by {@code file_offset}.
     */
    private static ScanResult scanCandidates(HeapView view, int batchSize) throws SQLException {
        int workers = Math.clamp(Runtime.getRuntime().availableProcessors(), MIN_WORKERS, MAX_WORKERS);
        Semaphore inFlight = new Semaphore(workers * IN_FLIGHT_BATCHES_PER_WORKER);
        List<Future<PartialResult>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             PreparedStatement stmt = view.databaseClient().connection().prepareStatement(CANDIDATE_ARRAYS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            List<CandidateRow> batch = new ArrayList<>(batchSize);
            while (rs.next()) {
                batch.add(new CandidateRow(
                        rs.getLong(1),
                        rs.getInt(2),
                        rs.getByte(3),
                        rs.getLong(4),
                        rs.getLong(5),
                        rs.getString(6)));
                if (batch.size() >= batchSize) {
                    futures.add(submitBatch(executor, view, batch, inFlight));
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) {
                futures.add(submitBatch(executor, view, batch, inFlight));
            }
        }

        Map<GroupKey, GroupAcc> groups = new HashMap<>();
        long oversizedSkipped = 0;
        for (Future<PartialResult> future : futures) {
            PartialResult partial = FutureJoin.unwrap(future);
            oversizedSkipped += partial.oversizedSkipped();
            mergeInto(groups, partial.groups());
        }
        return new ScanResult(groups, oversizedSkipped);
    }

    private static Future<PartialResult> submitBatch(
            ExecutorService executor, HeapView view, List<CandidateRow> batch, Semaphore inFlight) {
        inFlight.acquireUninterruptibly();
        return executor.submit(() -> {
            try {
                return processBatch(view, batch);
            } finally {
                inFlight.release();
            }
        });
    }

    private static PartialResult processBatch(HeapView view, List<CandidateRow> batch) {
        XXHash64 hasher = XXHashFactory.fastestJavaInstance().hash64();
        Map<GroupKey, GroupAcc> local = new HashMap<>();
        long oversizedSkipped = 0;
        for (CandidateRow row : batch) {
            int elementSize = HprofTag.BasicType.sizeOf(row.primitiveType());
            long contentBytes = (long) row.arrayLength() * Math.max(elementSize, 1);
            if (contentBytes < MIN_CONTENT_BYTES) {
                continue;
            }
            if (contentBytes > MAX_HASH_BYTES) {
                oversizedSkipped++;
                continue;
            }
            byte[] content = view.readInstanceContentBytes(row.toInstanceRow());
            if (content.length == 0) {
                continue;
            }
            long hash = hasher.hash(content, 0, content.length, HASH_SEED);
            GroupKey key = new GroupKey(row.primitiveType(), row.arrayLength(), hash);
            GroupAcc acc = local.computeIfAbsent(key, k -> new GroupAcc(row.typeName(), row.shallowSize()));
            acc.add(row.instanceId());
        }
        return new PartialResult(local, oversizedSkipped);
    }

    private static void mergeInto(Map<GroupKey, GroupAcc> target, Map<GroupKey, GroupAcc> source) {
        for (Map.Entry<GroupKey, GroupAcc> e : source.entrySet()) {
            GroupAcc existing = target.get(e.getKey());
            if (existing == null) {
                target.put(e.getKey(), e.getValue());
            } else {
                existing.merge(e.getValue());
            }
        }
    }

    /**
     * Human preview of the shared content: decoded text for byte/char arrays
     * that look textual, a hex prefix otherwise.
     */
    private static String renderPreview(HeapView view, long instanceId, byte primitiveType) {
        try {
            byte[] prefix = view.readPrimitiveArrayBytes(instanceId, PREVIEW_BYTES);
            if (prefix.length == 0) {
                return "";
            }
            if (primitiveType == HprofTag.BasicType.BYTE && isMostlyPrintableAscii(prefix)) {
                return truncate(new String(prefix, StandardCharsets.US_ASCII));
            }
            if (primitiveType == HprofTag.BasicType.CHAR) {
                return truncate(new String(prefix, StandardCharsets.UTF_16BE));
            }
            return truncate(toHex(prefix));
        } catch (SQLException | RuntimeException e) {
            return "";
        }
    }

    private static boolean isMostlyPrintableAscii(byte[] bytes) {
        int printable = 0;
        for (byte b : bytes) {
            if (b >= 0x20 && b < 0x7F) {
                printable++;
            }
        }
        return printable * 10 >= bytes.length * 9;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(Math.min(bytes.length, 16) * 3);
        for (int i = 0; i < bytes.length && i < 16; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private static String truncate(String value) {
        String sanitized = value.strip();
        if (sanitized.length() <= PREVIEW_MAX_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, PREVIEW_MAX_CHARS) + "…";
    }

    private record GroupKey(byte primitiveType, int arrayLength, long contentHash) {
    }

    /** A candidate primitive-array row carrying enough to read its payload without a PK lookup. */
    private record CandidateRow(
            long instanceId,
            int arrayLength,
            byte primitiveType,
            long shallowSize,
            long fileOffset,
            String typeName) {

        InstanceRow toInstanceRow() {
            return new InstanceRow(
                    instanceId,
                    null,
                    fileOffset,
                    InstanceRow.Kind.PRIMITIVE_ARRAY,
                    (int) shallowSize,
                    arrayLength,
                    (int) primitiveType);
        }
    }

    private record PartialResult(Map<GroupKey, GroupAcc> groups, long oversizedSkipped) {
    }

    private record ScanResult(Map<GroupKey, GroupAcc> groups, long oversizedSkipped) {
    }

    private static final class GroupAcc {

        private final String typeName;
        private final long shallowSize;
        private final long[] samples = new long[SAMPLE_IDS_PER_GROUP];
        private long firstInstanceId;
        private int sampleCount;
        private int count;

        private GroupAcc(String typeName, long shallowSize) {
            this.typeName = typeName;
            this.shallowSize = shallowSize;
        }

        void add(long instanceId) {
            if (count == 0) {
                firstInstanceId = instanceId;
            }
            if (sampleCount < SAMPLE_IDS_PER_GROUP) {
                samples[sampleCount++] = instanceId;
            }
            count++;
        }

        void merge(GroupAcc other) {
            if (count == 0 && other.count > 0) {
                firstInstanceId = other.firstInstanceId;
            }
            for (int i = 0; i < other.sampleCount && sampleCount < SAMPLE_IDS_PER_GROUP; i++) {
                samples[sampleCount++] = other.samples[i];
            }
            count += other.count;
        }

        long wastedBytes() {
            return (long) (count - 1) * shallowSize;
        }

        List<Long> sampleIds() {
            List<Long> out = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                out.add(samples[i]);
            }
            return out;
        }
    }
}
