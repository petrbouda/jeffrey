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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.DuplicateObjectEntry;
import pbouda.jeffrey.profile.heapdump.model.DuplicateObjectsReport;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Analyzes heap dumps for duplicate objects beyond strings.
 * Detects duplicate byte[] arrays, char[] arrays, and common boxed types
 * (Integer, Long, Short, Byte, Boolean, Float, Double, Character) with identical values.
 * <p>
 * For primitive arrays, content is hashed and grouped; duplicates are identified
 * when multiple instances share the same content hash. Sampling is used for
 * very large instance sets (>100k instances) to keep analysis time reasonable.
 */
public class DuplicateObjectAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateObjectAnalyzer.class);

    private static final int DEFAULT_TOP_N = 100;
    private static final int SAMPLING_THRESHOLD = 100_000;
    private static final int MAX_CONTENT_PREVIEW_LENGTH = 80;

    private static final List<String> PRIMITIVE_ARRAY_TYPES = List.of(
            "byte[]",
            "char[]"
    );

    private static final List<String> BOXED_TYPES = List.of(
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Boolean",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Character"
    );

    /**
     * Analyze the heap for duplicate objects.
     *
     * @param heap the loaded heap dump
     * @param topN number of top duplicate groups to return
     * @return complete duplicate objects report
     */
    public DuplicateObjectsReport analyze(Heap heap, int topN) {
        if (topN <= 0) {
            topN = DEFAULT_TOP_N;
        }

        long totalInstancesAnalyzed = 0;
        List<DuplicateObjectEntry> allDuplicates = new ArrayList<>();

        // Analyze primitive array types
        for (String arrayType : PRIMITIVE_ARRAY_TYPES) {
            JavaClass javaClass = heap.getJavaClassByName(arrayType);
            if (javaClass == null) {
                LOG.debug("Class not found in heap: className={}", arrayType);
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            totalInstancesAnalyzed += instances.size();

            LOG.info("Analyzing duplicate primitive arrays: className={} instanceCount={}", arrayType, instances.size());

            List<Instance> sampled = maybeSample(instances, arrayType);
            List<DuplicateObjectEntry> duplicates = findPrimitiveArrayDuplicates(sampled, arrayType);
            allDuplicates.addAll(duplicates);
        }

        // Analyze boxed types
        for (String boxedType : BOXED_TYPES) {
            JavaClass javaClass = heap.getJavaClassByName(boxedType);
            if (javaClass == null) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            totalInstancesAnalyzed += instances.size();

            if (instances.size() < 2) {
                continue;
            }

            LOG.info("Analyzing duplicate boxed types: className={} instanceCount={}", boxedType, instances.size());

            List<Instance> sampled = maybeSample(instances, boxedType);
            List<DuplicateObjectEntry> duplicates = findBoxedTypeDuplicates(sampled, boxedType);
            allDuplicates.addAll(duplicates);
        }

        // Sort by wasted bytes descending and take top N
        allDuplicates.sort(Comparator.comparingLong(DuplicateObjectEntry::totalWastedBytes).reversed());
        List<DuplicateObjectEntry> topDuplicates = allDuplicates.subList(
                0, Math.min(topN, allDuplicates.size()));

        long totalWastedBytes = topDuplicates.stream()
                .mapToLong(DuplicateObjectEntry::totalWastedBytes)
                .sum();

        LOG.info("Duplicate object analysis complete: totalInstancesAnalyzed={} duplicateGroupsFound={} totalWastedBytes={}",
                totalInstancesAnalyzed, topDuplicates.size(), totalWastedBytes);

        return new DuplicateObjectsReport(
                totalInstancesAnalyzed,
                totalWastedBytes,
                new ArrayList<>(topDuplicates)
        );
    }

    /**
     * Sample instances if the set is larger than the sampling threshold.
     * Uses reservoir sampling to get a representative subset.
     */
    private List<Instance> maybeSample(List<Instance> instances, String className) {
        if (instances.size() <= SAMPLING_THRESHOLD) {
            return instances;
        }

        LOG.info("Sampling instances due to large set: className={} total={} sampleSize={}",
                className, instances.size(), SAMPLING_THRESHOLD);

        // Reservoir sampling
        List<Instance> sampled = new ArrayList<>(SAMPLING_THRESHOLD);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < instances.size(); i++) {
            if (i < SAMPLING_THRESHOLD) {
                sampled.add(instances.get(i));
            } else {
                int j = random.nextInt(i + 1);
                if (j < SAMPLING_THRESHOLD) {
                    sampled.set(j, instances.get(i));
                }
            }
        }
        return sampled;
    }

    /**
     * Find duplicate primitive arrays (byte[] or char[]) by hashing their content.
     */
    @SuppressWarnings("unchecked")
    private List<DuplicateObjectEntry> findPrimitiveArrayDuplicates(List<Instance> instances, String className) {
        // Map: content hash -> (first instance, count, size)
        Map<ContentKey, DuplicateGroup> groups = new HashMap<>();

        for (Instance instance : instances) {
            if (!(instance instanceof PrimitiveArrayInstance arrayInstance)) {
                continue;
            }

            List<String> values = (List<String>) arrayInstance.getValues();
            if (values.isEmpty()) {
                continue;
            }

            byte[] contentHash = hashPrimitiveArrayContent(values);
            if (contentHash == null) {
                continue;
            }

            ContentKey key = new ContentKey(contentHash);
            long size = instance.getSize();

            groups.computeIfAbsent(key, k -> new DuplicateGroup(className, values, size))
                    .incrementCount();
        }

        // Collect entries with duplicates (count > 1)
        List<DuplicateObjectEntry> duplicates = new ArrayList<>();
        for (DuplicateGroup group : groups.values()) {
            if (group.count > 1) {
                long wastedBytes = group.individualSize * (group.count - 1);
                duplicates.add(new DuplicateObjectEntry(
                        className,
                        formatArrayPreview(group.sampleValues, className),
                        group.count,
                        group.individualSize,
                        wastedBytes
                ));
            }
        }
        return duplicates;
    }

    /**
     * Find duplicate boxed type instances by grouping on their value field.
     */
    private List<DuplicateObjectEntry> findBoxedTypeDuplicates(List<Instance> instances, String className) {
        // Map: stringified value -> (count, size)
        Map<String, DuplicateGroup> groups = new HashMap<>();

        String valueFieldName = "value";

        for (Instance instance : instances) {
            Object value = instance.getValueOfField(valueFieldName);
            if (value == null) {
                continue;
            }

            String valueStr = value.toString();
            long size = instance.getSize();

            groups.computeIfAbsent(valueStr, k -> new DuplicateGroup(className, null, size))
                    .incrementCount();
        }

        // Collect entries with duplicates (count > 1)
        List<DuplicateObjectEntry> duplicates = new ArrayList<>();
        for (Map.Entry<String, DuplicateGroup> entry : groups.entrySet()) {
            DuplicateGroup group = entry.getValue();
            if (group.count > 1) {
                long wastedBytes = group.individualSize * (group.count - 1);
                duplicates.add(new DuplicateObjectEntry(
                        className,
                        entry.getKey(),
                        group.count,
                        group.individualSize,
                        wastedBytes
                ));
            }
        }
        return duplicates;
    }

    /**
     * Hash the content of a primitive array using MD5 for fast duplicate detection.
     */
    private byte[] hashPrimitiveArrayContent(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            for (String val : values) {
                try {
                    long parsed = Long.parseLong(val);
                    buffer.clear();
                    buffer.putLong(parsed);
                    digest.update(buffer.array());
                } catch (NumberFormatException e) {
                    digest.update(val.getBytes());
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("MD5 algorithm not available");
            return null;
        }
    }

    /**
     * Format a preview of array content for display.
     */
    private String formatArrayPreview(List<String> values, String className) {
        if (values == null || values.isEmpty()) {
            return "<empty>";
        }

        if ("byte[]".equals(className)) {
            return formatByteArrayPreview(values);
        } else if ("char[]".equals(className)) {
            return formatCharArrayPreview(values);
        }
        return "<" + values.size() + " elements>";
    }

    private String formatByteArrayPreview(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        // Show up to first 20 byte values
        int limit = Math.min(values.size(), 20);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            try {
                int byteVal = Integer.parseInt(values.get(i)) & 0xFF;
                sb.append(String.format("%02x", byteVal));
            } catch (NumberFormatException e) {
                sb.append("??");
            }
        }

        if (values.size() > 20) {
            sb.append(", ... (").append(values.size()).append(" bytes)");
        }
        sb.append("]");

        return truncate(sb.toString(), MAX_CONTENT_PREVIEW_LENGTH);
    }

    private String formatCharArrayPreview(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");

        int limit = Math.min(values.size(), 40);
        for (int i = 0; i < limit; i++) {
            try {
                int charVal = Integer.parseInt(values.get(i));
                if (charVal >= 32 && charVal < 127) {
                    sb.append((char) charVal);
                } else {
                    sb.append("\\u").append(String.format("%04x", charVal));
                }
            } catch (NumberFormatException e) {
                sb.append("?");
            }
        }

        if (values.size() > 40) {
            sb.append("... (").append(values.size()).append(" chars)");
        }
        sb.append("\"");

        return truncate(sb.toString(), MAX_CONTENT_PREVIEW_LENGTH);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * Wrapper for byte[] to use as HashMap key (since byte[] doesn't implement equals/hashCode).
     */
    private record ContentKey(byte[] hash) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ContentKey that)) return false;
            return Arrays.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }
    }

    /**
     * Mutable group accumulator for tracking duplicate instances.
     */
    private static class DuplicateGroup {
        final String className;
        final List<String> sampleValues;
        final long individualSize;
        int count;

        DuplicateGroup(String className, List<String> sampleValues, long individualSize) {
            this.className = className;
            this.sampleValues = sampleValues;
            this.individualSize = individualSize;
            this.count = 0;
        }

        void incrementCount() {
            count++;
        }
    }
}
