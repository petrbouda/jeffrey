/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import pbouda.jeffrey.profile.heapdump.model.StringAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.StringDeduplicationEntry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes String instances in a heap dump for deduplication status and opportunities.
 * Supports Java 9+ compact strings (byte[] value + coder field).
 * <p>
 * Deduplication detection:
 * - Deduplicated: Multiple String instances pointing to the same byte[] instance ID
 * - Opportunity: Multiple String instances with identical content but different byte[] arrays
 */
public class StringAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    private static final int MAX_CONTENT_LENGTH = 100;

    /**
     * Analyze string deduplication in the heap.
     *
     * @param heap the loaded heap dump
     * @param topN number of top entries to return for each list
     * @return complete analysis report
     */
    @SuppressWarnings("unchecked")
    public StringAnalysisReport analyze(Heap heap, int topN) {
        return analyze(heap, topN, false);
    }

    /**
     * Analyze string deduplication with compressed oops correction.
     *
     * @param heap           the loaded heap dump
     * @param topN           number of top entries to return for each list
     * @param compressedOops whether compressed oops are enabled
     * @return complete analysis report
     */
    @SuppressWarnings("unchecked")
    public StringAnalysisReport analyze(Heap heap, int topN, boolean compressedOops) {
        if (topN <= 0) {
            topN = DEFAULT_TOP_N;
        }

        JavaClass stringClass = heap.getJavaClassByName("java.lang.String");
        if (stringClass == null) {
            return emptyReport();
        }

        // Map: value array instance ID -> list of String instances sharing it
        Map<Long, List<Instance>> valueArrayToStrings = new HashMap<>();
        Map<Long, Long> valueArraySizes = new HashMap<>();

        long totalStrings = 0;
        long totalStringShallowSize = 0;

        List<Instance> stringInstances = (List<Instance>) stringClass.getInstances();
        for (Instance stringInstance : stringInstances) {
            totalStrings++;
            totalStringShallowSize += CompressedOopsCorrector.correctedShallowSize(stringInstance, compressedOops);

            Object valueField = stringInstance.getValueOfField("value");

            if (valueField instanceof Instance valueArray) {
                long valueArrayId = valueArray.getInstanceId();

                valueArrayToStrings
                        .computeIfAbsent(valueArrayId, k -> new ArrayList<>())
                        .add(stringInstance);

                valueArraySizes.putIfAbsent(valueArrayId, valueArray.getSize());
            }
        }

        long uniqueArrays = valueArrayToStrings.size();
        long sharedArrays = 0;
        long totalSharedStrings = 0;
        long memorySavedByDedup = 0;

        List<StringDeduplicationEntry> alreadyDeduplicated = new ArrayList<>();
        Map<String, List<Instance>> contentToStrings = new HashMap<>();

        for (var entry : valueArrayToStrings.entrySet()) {
            List<Instance> strings = entry.getValue();
            long arraySize = valueArraySizes.get(entry.getKey());

            if (strings.size() > 1) {
                // Array IS shared (deduplication active)
                sharedArrays++;
                totalSharedStrings += strings.size();
                memorySavedByDedup += (long) (strings.size() - 1) * arraySize;

                alreadyDeduplicated.add(new StringDeduplicationEntry(
                        getStringValue(strings.get(0)),
                        strings.size(),
                        arraySize,
                        (long) (strings.size() - 1) * arraySize
                ));
            } else {
                // Single use - check for same content with different arrays
                String content = getStringValue(strings.get(0));
                if (content != null) {
                    contentToStrings
                            .computeIfAbsent(content, k -> new ArrayList<>())
                            .add(strings.get(0));
                }
            }
        }

        // Find deduplication opportunities
        long potentialSavings = 0;
        List<StringDeduplicationEntry> opportunities = new ArrayList<>();

        for (var entry : contentToStrings.entrySet()) {
            if (entry.getValue().size() > 1) {
                Instance first = entry.getValue().get(0);
                Instance valueArray = (Instance) first.getValueOfField("value");
                long arraySize = valueArray != null ? valueArray.getSize() : 0;
                long savings = (long) (entry.getValue().size() - 1) * arraySize;
                potentialSavings += savings;

                opportunities.add(new StringDeduplicationEntry(
                        entry.getKey(),
                        entry.getValue().size(),
                        arraySize,
                        savings
                ));
            }
        }

        // Sort by savings descending and limit to topN
        alreadyDeduplicated.sort(Comparator.comparingLong(StringDeduplicationEntry::savings).reversed());
        opportunities.sort(Comparator.comparingLong(StringDeduplicationEntry::savings).reversed());

        List<StringDeduplicationEntry> topDeduplicated = alreadyDeduplicated.subList(
                0, Math.min(topN, alreadyDeduplicated.size()));
        List<StringDeduplicationEntry> topOpportunities = opportunities.subList(
                0, Math.min(topN, opportunities.size()));

        return new StringAnalysisReport(
                totalStrings,
                totalStringShallowSize,
                uniqueArrays,
                sharedArrays,
                totalSharedStrings,
                memorySavedByDedup,
                potentialSavings,
                new ArrayList<>(topDeduplicated),
                new ArrayList<>(topOpportunities),
                List.of()  // JVM flags will be added by HeapDumpManager
        );
    }

    /**
     * Extract String value from heap dump instance.
     * Java 9+ compact strings: byte[] value + byte coder (0=LATIN1, 1=UTF16)
     */
    private String getStringValue(Instance stringInstance) {
        try {
            Object valueField = stringInstance.getValueOfField("value");
            if (!(valueField instanceof PrimitiveArrayInstance array)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<String> values = (List<String>) array.getValues();
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < values.size(); i++) {
                bytes[i] = Byte.parseByte(values.get(i));
            }

            // coder: 0 = LATIN1, 1 = UTF16
            Object coder = stringInstance.getValueOfField("coder");
            boolean isLatin1 = coder == null || ((Number) coder).intValue() == 0;

            String result = isLatin1
                    ? new String(bytes, StandardCharsets.ISO_8859_1)
                    : new String(bytes, StandardCharsets.UTF_16LE);

            return truncate(result, MAX_CONTENT_LENGTH);

        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private StringAnalysisReport emptyReport() {
        return new StringAnalysisReport(
                0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of()
        );
    }
}
