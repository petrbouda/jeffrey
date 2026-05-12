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

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.model.DuplicateObjectEntry;
import cafe.jeffrey.profile.heapdump.model.DuplicateObjectsReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.shared.common.measure.Measuring;

/**
 * Finds instances with byte-identical content within a class.
 *
 * <p>The work is layered so the expensive byte-read + hash step only ever
 * happens for instances that could plausibly form a duplicate group:
 * <ol>
 *   <li>SQL pre-filter: {@code GROUP BY (class_id, shallow_size) HAVING
 *       COUNT(*) >= 2} — single-instance classes and unique-by-length object
 *       arrays drop out before any byte read.</li>
 *   <li>Greedy bin-pack the surviving {@code (class_id, shallow_size)} pairs
 *       into {@code N = availableProcessors} buckets, balanced by instance
 *       count.</li>
 *   <li>Per bucket, on a virtual thread: open an independent read-only view,
 *       stream the bucket's instances ordered by {@code file_offset} for
 *       mmap page-cache locality, read content bytes directly from the row
 *       (no second DuckDB lookup), hash with xxhash64.</li>
 *   <li>Group by {@code (class_id, xxhash64)}. Hash collisions are resolved
 *       by a chain of distinct-bytes exemplars within each group entry; a
 *       new instance increments the count only after byte-equality with one
 *       of the exemplars.</li>
 *   <li>Merge per-bucket maps; emit groups with {@code count > 1}; sort by
 *       wasted bytes; truncate to top N.</li>
 * </ol>
 */
public final class DuplicateObjectAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateObjectAnalyzer.class);

    private static final int DEFAULT_TOP_N = 100;
    /** Skip tiny instances — their dedup wins are negligible and noise dominates. */
    private static final int MIN_SHALLOW_SIZE = 16;

    private static final XXHash64 XX = XXHashFactory.fastestInstance().hash64();
    private static final long HASH_SEED = 0L;

    private DuplicateObjectAnalyzer() {
    }

    public static DuplicateObjectsReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static DuplicateObjectsReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        Map<Long, String> classNames = loadClassNames(view);
        List<Candidate> candidates = loadCandidates(view, MIN_SHALLOW_SIZE);
        if (candidates.isEmpty()) {
            return new DuplicateObjectsReport(0L, 0L, List.of());
        }

        int parallelism = Math.min(candidates.size(),
                Math.max(1, Runtime.getRuntime().availableProcessors()));
        List<Bucket> buckets = binPack(candidates, parallelism);

        var elapsed = Measuring.s(() -> runParallel(view, buckets));
        AggregateResult agg = elapsed.entity();
        LOG.debug("Duplicate-objects scan: candidates={} buckets={} processed={} groups={} duration_in_ms={}",
                candidates.size(), buckets.size(), agg.totalProcessed,
                agg.merged.size(), elapsed.duration().toMillis());

        return buildReport(view, agg.merged, classNames, agg.totalProcessed, topN);
    }

    // ---- Phase 1: aggregate metadata ------------------------------------

    private static Map<Long, String> loadClassNames(HeapView view) throws SQLException {
        Map<Long, String> out = new HashMap<>();
        try (var stmt = view.connection().createStatement();
                ResultSet rs = stmt.executeQuery("SELECT class_id, name FROM class")) {
            while (rs.next()) {
                out.put(rs.getLong(1), rs.getString(2));
            }
        }
        return out;
    }

    /**
     * One SQL pass: every {@code (class_id, shallow_size)} pair whose instance
     * count is at least 2, ordered by count descending so the bin-packer can
     * place the heaviest pairs first.
     */
    private static List<Candidate> loadCandidates(HeapView view, int minShallowSize) throws SQLException {
        List<Candidate> out = new ArrayList<>();
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT class_id, shallow_size, COUNT(*) "
                        + "FROM instance "
                        + "WHERE class_id IS NOT NULL AND shallow_size >= ? AND record_kind != 2 "
                        + "GROUP BY class_id, shallow_size "
                        + "HAVING COUNT(*) >= 2 "
                        + "ORDER BY COUNT(*) DESC")) {
            stmt.setInt(1, minShallowSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new Candidate(rs.getLong(1), rs.getInt(2), rs.getLong(3)));
                }
            }
        }
        return out;
    }

    // ---- Phase 2: bucket partitioning -----------------------------------

    private static List<Bucket> binPack(List<Candidate> candidates, int n) {
        List<Bucket> buckets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            buckets.add(new Bucket());
        }
        long[] loads = new long[n];
        for (Candidate c : candidates) {
            int minIdx = 0;
            for (int i = 1; i < n; i++) {
                if (loads[i] < loads[minIdx]) {
                    minIdx = i;
                }
            }
            Bucket b = buckets.get(minIdx);
            if (!b.allowed.containsKey(c.classId)) {
                b.classIds.add(c.classId);
            }
            b.allowed.computeIfAbsent(c.classId, k -> new HashSet<>()).add(c.shallowSize);
            loads[minIdx] += c.count;
        }
        buckets.removeIf(b -> b.classIds.isEmpty());
        return buckets;
    }

    // ---- Phase 3: fan-out + merge ---------------------------------------

    private static AggregateResult runParallel(HeapView view, List<Bucket> buckets) {
        try (ExecutorService vexec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<BucketResult>> futures = buckets.stream()
                    .map(b -> CompletableFuture.supplyAsync(() -> processBucket(view, b), vexec))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();

            Map<Group, AccHead> merged = new HashMap<>();
            long totalProcessed = 0L;
            for (CompletableFuture<BucketResult> f : futures) {
                BucketResult r = f.join();
                totalProcessed += r.processed;
                mergeInto(merged, r.groups);
            }
            return new AggregateResult(merged, totalProcessed);
        }
    }

    private static BucketResult processBucket(HeapView parent, Bucket bucket) {
        try (HeapView local = parent.openReadOnlyCopy()) {
            Map<Group, AccHead> groups = new HashMap<>();
            long processed = 0L;

            String sql = buildBucketSql(bucket.classIds.size());
            try (PreparedStatement stmt = local.connection().prepareStatement(sql)) {
                int p = 1;
                for (Long id : bucket.classIds) {
                    stmt.setLong(p++, id);
                }
                stmt.setInt(p, MIN_SHALLOW_SIZE);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long instanceId = rs.getLong(1);
                        long classId = rs.getLong(2);
                        long fileOffset = rs.getLong(3);
                        int recordKind = rs.getInt(4);
                        int shallowSize = rs.getInt(5);
                        int arrayLengthRaw = rs.getInt(6);
                        Integer arrayLength = rs.wasNull() ? null : arrayLengthRaw;
                        int primitiveTypeRaw = rs.getInt(7);
                        Integer primitiveType = rs.wasNull() ? null : primitiveTypeRaw;

                        // In-Java tuple filter: instances of a class with a
                        // shallow_size that isn't in this bucket's candidate set
                        // (other arrays of the same class with different length)
                        // could come back from the IN-list query — drop them.
                        Set<Integer> allowedSizes = bucket.allowed.get(classId);
                        if (allowedSizes == null || !allowedSizes.contains(shallowSize)) {
                            continue;
                        }

                        InstanceRow row = new InstanceRow(
                                instanceId, classId, fileOffset,
                                InstanceRow.Kind.fromOrdinal(recordKind),
                                shallowSize, arrayLength, primitiveType);

                        byte[] content = local.readInstanceContentBytes(row);
                        if (content.length == 0) {
                            continue;
                        }
                        long h = XX.hash(content, 0, content.length, HASH_SEED);
                        record(groups, classId, h, content, shallowSize, instanceId);
                        processed++;
                    }
                }
            }
            return new BucketResult(groups, processed);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(
                    "Failed processing duplicate-objects bucket: " + e.getMessage(), e);
        }
    }

    private static String buildBucketSql(int classCount) {
        StringBuilder sql = new StringBuilder(
                "SELECT instance_id, class_id, file_offset, record_kind, shallow_size, "
                        + "array_length, primitive_type FROM instance "
                        + "WHERE class_id IN (");
        for (int i = 0; i < classCount; i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(") AND shallow_size >= ? AND record_kind != 2 ORDER BY file_offset");
        return sql.toString();
    }

    private static void record(Map<Group, AccHead> groups, long classId, long h,
            byte[] content, int shallowSize, long instanceId) {
        Group key = new Group(classId, h);
        AccHead head = groups.get(key);
        if (head == null) {
            groups.put(key, new AccHead(content, shallowSize, instanceId));
            return;
        }
        for (AccHead cur = head; cur != null; cur = cur.next) {
            if (Arrays.equals(cur.exemplar, content)) {
                cur.count++;
                return;
            }
        }
        // xxhash64 collision with distinct bytes — prepend a new chain node so
        // the two byte sequences stay distinct groups in the output.
        AccHead newHead = new AccHead(content, shallowSize, instanceId);
        newHead.next = head;
        groups.put(key, newHead);
    }

    private static void mergeInto(Map<Group, AccHead> dst, Map<Group, AccHead> src) {
        for (Map.Entry<Group, AccHead> e : src.entrySet()) {
            Group key = e.getKey();
            for (AccHead srcNode = e.getValue(); srcNode != null; srcNode = srcNode.next) {
                AccHead dstHead = dst.get(key);
                boolean folded = false;
                for (AccHead w = dstHead; w != null; w = w.next) {
                    if (Arrays.equals(w.exemplar, srcNode.exemplar)) {
                        w.count += srcNode.count;
                        folded = true;
                        break;
                    }
                }
                if (!folded) {
                    AccHead copy = new AccHead(
                            srcNode.exemplar, srcNode.shallowSize, srcNode.exemplarInstanceId);
                    copy.count = srcNode.count;
                    copy.next = dstHead;
                    dst.put(key, copy);
                }
            }
        }
    }

    // ---- Phase 4: report build ------------------------------------------

    private static DuplicateObjectsReport buildReport(
            HeapView view,
            Map<Group, AccHead> merged, Map<Long, String> classNames,
            long totalProcessed, int topN) {
        List<DuplicateObjectEntry> entries = new ArrayList<>();
        long totalWasted = 0L;
        for (Map.Entry<Group, AccHead> e : merged.entrySet()) {
            for (AccHead node = e.getValue(); node != null; node = node.next) {
                if (node.count <= 1) {
                    continue;
                }
                long wasted = (long) (node.count - 1) * node.shallowSize;
                totalWasted += wasted;
                String className = classNames.getOrDefault(e.getKey().classId(), "<unknown>");
                String preview = ContentPreviewRenderer.render(
                        view, className, node.exemplarInstanceId, node.exemplar);
                entries.add(new DuplicateObjectEntry(
                        className, preview,
                        node.count, node.shallowSize, wasted));
            }
        }
        entries.sort(Comparator.comparingLong(DuplicateObjectEntry::totalWastedBytes).reversed());
        if (entries.size() > topN) {
            entries = entries.subList(0, topN);
        }
        return new DuplicateObjectsReport(totalProcessed, totalWasted, List.copyOf(entries));
    }

    // ---- Internal types -------------------------------------------------

    private record Candidate(long classId, int shallowSize, long count) {
    }

    private record Group(long classId, long xxHash) {
    }

    private record BucketResult(Map<Group, AccHead> groups, long processed) {
    }

    private record AggregateResult(Map<Group, AccHead> merged, long totalProcessed) {
    }

    private static final class Bucket {
        final List<Long> classIds = new ArrayList<>();
        final Map<Long, Set<Integer>> allowed = new HashMap<>();
    }

    private static final class AccHead {
        final byte[] exemplar;
        final int shallowSize;
        final long exemplarInstanceId;
        int count = 1;
        AccHead next;

        AccHead(byte[] exemplar, int shallowSize, long exemplarInstanceId) {
            this.exemplar = exemplar;
            this.shallowSize = shallowSize;
            this.exemplarInstanceId = exemplarInstanceId;
        }
    }
}
