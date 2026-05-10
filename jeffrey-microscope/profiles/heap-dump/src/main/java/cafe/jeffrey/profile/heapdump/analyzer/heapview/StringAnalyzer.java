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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.StringDeduplicationEntry;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.StringAnalyzer}.
 *
 * Iterates every {@code java.lang.String} instance, decodes it via
 * {@link JavaStringDecoder} (handles both Java 8 char[] and Java 9+
 * compact byte[] layouts), and groups the results to surface:
 * <ul>
 *   <li><strong>Already deduplicated</strong>: distinct value-arrays shared
 *       by multiple String objects. Savings = (sharers - 1) × arrayShallow.</li>
 *   <li><strong>Opportunities</strong>: identical content stored in multiple
 *       distinct value-arrays. Potential savings = (count - 1) × arrayShallow.</li>
 * </ul>
 *
 * JVM-flag enrichment from JFR events stays the caller's job — this analyzer
 * returns an empty {@link JvmStringFlag} list.
 */
public final class StringAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    private static final String JAVA_LANG_STRING = "java.lang.String";

    private StringAnalyzer() {
    }

    public static StringAnalysisReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static StringAnalysisReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        Map<Long, ArrayUsage> byArray = new HashMap<>();
        Map<String, ContentUsage> byContent = new HashMap<>();
        long totalStrings = 0;
        long totalShallowSize = 0;

        for (JavaClassRow stringClass : view.findClassesByName(JAVA_LANG_STRING)) {
            try (Stream<InstanceRow> stream = view.instances(stringClass.classId())) {
                for (InstanceRow inst : (Iterable<InstanceRow>) stream::iterator) {
                    Optional<JavaStringDecoder.Decoded> opt = JavaStringDecoder.decode(view, inst.instanceId());
                    if (opt.isEmpty()) {
                        continue;
                    }
                    JavaStringDecoder.Decoded d = opt.get();
                    totalStrings++;
                    totalShallowSize += inst.shallowSize();

                    ArrayUsage au = byArray.computeIfAbsent(d.valueArrayId(),
                            id -> new ArrayUsage(d.content(), d.valueArrayBytes()));
                    au.refCount++;

                    ContentUsage cu = byContent.computeIfAbsent(d.content(),
                            c -> new ContentUsage(d.valueArrayBytes()));
                    cu.totalCount++;
                    cu.distinctArrays.add(d.valueArrayId());
                }
            }
        }

        long uniqueArrays = byArray.size();
        long sharedArrays = byArray.values().stream().filter(a -> a.refCount > 1).count();
        long totalSharedStrings = byArray.values().stream().filter(a -> a.refCount > 1)
                .mapToLong(a -> a.refCount).sum();
        long memorySavedByDedup = byArray.values().stream().filter(a -> a.refCount > 1)
                .mapToLong(a -> (long) (a.refCount - 1) * a.arrayShallowSize).sum();

        // Already-deduplicated: distinct value-arrays referenced by more than one String.
        List<StringDeduplicationEntry> already = byArray.values().stream()
                .filter(a -> a.refCount > 1)
                .map(a -> new StringDeduplicationEntry(
                        truncate(a.content),
                        a.refCount,
                        a.arrayShallowSize,
                        (long) (a.refCount - 1) * a.arrayShallowSize))
                .sorted(Comparator.comparingLong(StringDeduplicationEntry::savings).reversed())
                .limit(topN)
                .toList();

        // Opportunities: same content stored in multiple distinct arrays.
        List<StringDeduplicationEntry> opps = byContent.entrySet().stream()
                .filter(e -> e.getValue().distinctArrays.size() > 1)
                .map(e -> new StringDeduplicationEntry(
                        truncate(e.getKey()),
                        e.getValue().totalCount,
                        e.getValue().arrayShallowSize,
                        (long) (e.getValue().distinctArrays.size() - 1) * e.getValue().arrayShallowSize))
                .sorted(Comparator.comparingLong(StringDeduplicationEntry::savings).reversed())
                .limit(topN)
                .toList();

        long potentialSavings = byContent.values().stream()
                .filter(c -> c.distinctArrays.size() > 1)
                .mapToLong(c -> (long) (c.distinctArrays.size() - 1) * c.arrayShallowSize)
                .sum();

        return new StringAnalysisReport(
                totalStrings,
                totalShallowSize,
                uniqueArrays,
                sharedArrays,
                totalSharedStrings,
                memorySavedByDedup,
                potentialSavings,
                already,
                opps,
                List.<JvmStringFlag>of());
    }

    private static String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }

    private static final class ArrayUsage {
        final String content;
        final long arrayShallowSize;
        int refCount;

        ArrayUsage(String content, long arrayShallowSize) {
            this.content = content;
            this.arrayShallowSize = arrayShallowSize;
        }
    }

    private static final class ContentUsage {
        final long arrayShallowSize;
        final Set<Long> distinctArrays = new HashSet<>();
        int totalCount;

        ContentUsage(long arrayShallowSize) {
            this.arrayShallowSize = arrayShallowSize;
        }
    }
}
