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
import cafe.jeffrey.profile.heapdump.model.ClassLoaderInfo;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.DuplicateClassInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects class loader leaks and misconfigurations by analyzing the heap for:
 * <ul>
 *   <li><b>Duplicate classes</b>: same class loaded by multiple class loaders (indicates leak or misconfiguration)</li>
 *   <li><b>Class loader summary</b>: each class loader, how many classes it loaded, total size</li>
 *   <li><b>Large class loaders</b>: class loaders retaining disproportionate memory</li>
 * </ul>
 * Class loader leaks are a top cause of metaspace OOM in web applications (e.g. hot-redeployment leaks).
 */
public class ClassLoaderAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassLoaderAnalyzer.class);

    private static final String BOOTSTRAP_CLASS_LOADER = "<bootstrap>";

    /**
     * Analyze class loaders in the heap and produce a report.
     */
    @SuppressWarnings("unchecked")
    public ClassLoaderReport analyze(Heap heap) {
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();

        // Map: class loader instance ID -> accumulated info
        // Use 0L as the key for the bootstrap class loader (null classLoader)
        Map<Long, ClassLoaderAccumulator> classLoaderMap = new HashMap<>();

        // Map: fully qualified class name -> set of class loader class names (for duplicate detection)
        Map<String, Map<Long, String>> classToLoaders = new HashMap<>();

        for (JavaClass javaClass : allClasses) {
            Instance classLoader = javaClass.getClassLoader();
            long classLoaderId;
            String classLoaderClassName;

            if (classLoader == null) {
                classLoaderId = 0L;
                classLoaderClassName = BOOTSTRAP_CLASS_LOADER;
            } else {
                classLoaderId = classLoader.getInstanceId();
                classLoaderClassName = classLoader.getJavaClass().getName();
            }

            // Accumulate class loader statistics
            ClassLoaderAccumulator acc = classLoaderMap.computeIfAbsent(
                    classLoaderId,
                    id -> new ClassLoaderAccumulator(classLoaderId, classLoaderClassName));
            acc.classCount++;
            acc.totalClassSize += javaClass.getAllInstancesSize();

            // Track which class loaders loaded each class name (for duplicate detection)
            String className = javaClass.getName();
            classToLoaders.computeIfAbsent(className, k -> new LinkedHashMap<>())
                    .putIfAbsent(classLoaderId, classLoaderClassName);
        }

        // Compute retained sizes for non-bootstrap class loader instances
        for (ClassLoaderAccumulator acc : classLoaderMap.values()) {
            if (acc.objectId != 0L) {
                Instance instance = heap.getInstanceByID(acc.objectId);
                if (instance != null) {
                    acc.retainedSize = instance.getRetainedSize();
                }
            }
        }

        // Build class loader info list sorted by class count descending
        List<ClassLoaderInfo> classLoaderInfos = classLoaderMap.values().stream()
                .map(acc -> new ClassLoaderInfo(
                        acc.objectId,
                        acc.classLoaderClassName,
                        acc.classCount,
                        acc.totalClassSize,
                        acc.retainedSize))
                .sorted(Comparator.comparingInt(ClassLoaderInfo::classCount).reversed())
                .toList();

        // Find duplicate classes (loaded by more than one class loader)
        List<DuplicateClassInfo> duplicateClasses = new ArrayList<>();
        for (Map.Entry<String, Map<Long, String>> entry : classToLoaders.entrySet()) {
            Map<Long, String> loaders = entry.getValue();
            if (loaders.size() > 1) {
                duplicateClasses.add(new DuplicateClassInfo(
                        entry.getKey(),
                        loaders.size(),
                        new ArrayList<>(loaders.values())));
            }
        }

        // Sort duplicates by loader count descending, then by class name
        duplicateClasses.sort(Comparator
                .comparingInt(DuplicateClassInfo::loaderCount).reversed()
                .thenComparing(DuplicateClassInfo::className));

        LOG.info("Class loader analysis complete: totalClassLoaders={} totalClasses={} duplicateClasses={}",
                classLoaderInfos.size(), allClasses.size(), duplicateClasses.size());

        return new ClassLoaderReport(
                classLoaderInfos.size(),
                allClasses.size(),
                duplicateClasses.size(),
                classLoaderInfos,
                duplicateClasses);
    }

    private static class ClassLoaderAccumulator {
        final long objectId;
        final String classLoaderClassName;
        int classCount;
        long totalClassSize;
        long retainedSize;

        ClassLoaderAccumulator(long objectId, String classLoaderClassName) {
            this.objectId = objectId;
            this.classLoaderClassName = classLoaderClassName;
        }
    }
}
