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

package cafe.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderRef;
import cafe.jeffrey.profile.heapdump.model.ComponentEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link ConsumerReport}: retained-size aggregations grouped by
 * ({@code packageName}, {@code classLoader}) for top consumers, and by
 * {@code packageName} alone for the component report.
 * <p>
 * For each class in the heap, the analyzer:
 * <ol>
 *   <li>Resolves the class loader via {@link ClassLoaderResolver}.</li>
 *   <li>Iterates instances and accumulates retained / shallow size, with
 *       compressed-oops correction.</li>
 *   <li>Folds the per-class totals into per-(package,loader) and per-package buckets.</li>
 * </ol>
 */
public class ConsumerReportAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerReportAnalyzer.class);

    private static final int TOP_CONSUMERS_LIMIT = 50;
    private static final int COMPONENT_REPORT_LIMIT = 50;
    private static final String DEFAULT_PACKAGE = "<default>";

    @SuppressWarnings("unchecked")
    public ConsumerReport analyze(Heap heap, boolean compressedOops, double correctionRatio, long totalOvercount) {
        long rawTotalHeap = heap.getSummary().getTotalLiveBytes();
        long totalHeapSize = CompressedOopsCorrector.correctedTotalHeap(rawTotalHeap, compressedOops, totalOvercount);

        Map<ConsumerKey, ConsumerAcc> consumerMap = new HashMap<>();
        Map<String, ConsumerAcc> componentMap = new HashMap<>();

        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        for (JavaClass javaClass : allClasses) {
            int instanceCount = (int) javaClass.getInstancesCount();
            if (instanceCount == 0) {
                continue;
            }

            String packageName = packageOf(javaClass.getName());
            // Pick any instance to resolve the class loader (all instances share one)
            ClassLoaderRef loader = ClassLoaderResolver.refFor(firstInstance(javaClass));

            long classRetained = 0;
            long classShallow = 0;
            for (Instance instance : (List<Instance>) javaClass.getInstances()) {
                long retained = instance.getRetainedSize();
                if (retained <= 0) {
                    retained = instance.getSize();
                }
                classRetained += CompressedOopsCorrector.correctedRetainedSize(retained, compressedOops, correctionRatio);
                classShallow += CompressedOopsCorrector.correctedShallowSize(instance, compressedOops);
            }

            ConsumerKey key = new ConsumerKey(packageName, loader.classLoaderId(), loader.classLoaderClassName());
            consumerMap.computeIfAbsent(key, k -> new ConsumerAcc())
                    .add(classRetained, classShallow, 1, instanceCount);

            componentMap.computeIfAbsent(packageName, k -> new ConsumerAcc())
                    .add(classRetained, classShallow, 1, instanceCount);
        }

        List<ConsumerEntry> topConsumers = consumerMap.entrySet().stream()
                .map(e -> new ConsumerEntry(
                        e.getKey().packageName(),
                        e.getKey().classLoaderId(),
                        e.getKey().classLoaderClassName(),
                        e.getValue().retained,
                        e.getValue().shallow,
                        e.getValue().classCount,
                        e.getValue().instanceCount))
                .sorted(Comparator.comparingLong(ConsumerEntry::retainedSize).reversed())
                .limit(TOP_CONSUMERS_LIMIT)
                .toList();

        List<ComponentEntry> componentReport = componentMap.entrySet().stream()
                .map(e -> new ComponentEntry(
                        e.getKey(),
                        e.getValue().retained,
                        e.getValue().shallow,
                        e.getValue().classCount,
                        e.getValue().instanceCount))
                .sorted(Comparator.comparingLong(ComponentEntry::retainedSize).reversed())
                .limit(COMPONENT_REPORT_LIMIT)
                .toList();

        LOG.info("Consumer report complete: topConsumers={} components={} totalHeap={}",
                topConsumers.size(), componentReport.size(), totalHeapSize);

        return new ConsumerReport(totalHeapSize, topConsumers, componentReport);
    }

    @SuppressWarnings("unchecked")
    private static Instance firstInstance(JavaClass javaClass) {
        List<Instance> instances = (List<Instance>) javaClass.getInstances();
        return instances.isEmpty() ? null : instances.get(0);
    }

    static String packageOf(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) {
            return DEFAULT_PACKAGE;
        }
        // Strip array suffix
        String name = fullClassName;
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return DEFAULT_PACKAGE;
        }
        return name.substring(0, lastDot);
    }

    private record ConsumerKey(String packageName, long classLoaderId, String classLoaderClassName) {
    }

    private static final class ConsumerAcc {
        long retained;
        long shallow;
        int classCount;
        long instanceCount;

        void add(long retained, long shallow, int classCount, long instanceCount) {
            this.retained += retained;
            this.shallow += shallow;
            this.classCount += classCount;
            this.instanceCount += instanceCount;
        }
    }
}
