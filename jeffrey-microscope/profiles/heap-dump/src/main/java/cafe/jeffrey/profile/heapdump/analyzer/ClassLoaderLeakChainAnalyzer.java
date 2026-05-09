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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.model.CauseHint;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderInfo;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.DuplicateClassInfo;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.HintKind;
import cafe.jeffrey.profile.heapdump.model.PathStep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Diagnoses why suspicious class loaders are still alive in the heap.
 * <p>
 * For each suspicious loader (large retained size, in {@code duplicateClasses}, or a
 * webapp/URL classloader instance), runs {@link PathToGCRootAnalyzer} to find a reference
 * chain back to a GC root and annotates the chain with {@link CauseHint}s for well-known
 * leak patterns: ThreadLocal, JDBC driver registration, JNI globals, ServiceLoader,
 * static Logger / LogManager, and {@code Thread.contextClassLoader}.
 */
public class ClassLoaderLeakChainAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ClassLoaderLeakChainAnalyzer.class);

    private static final long LARGE_LOADER_BYTES = 50L * 1024 * 1024;
    private static final int MAX_PATHS_PER_LOADER = 1;
    private static final int MAX_LOADERS_TO_CHECK = 20;

    private final PathToGCRootAnalyzer pathToGCRootAnalyzer;

    public ClassLoaderLeakChainAnalyzer(PathToGCRootAnalyzer pathToGCRootAnalyzer) {
        this.pathToGCRootAnalyzer = pathToGCRootAnalyzer;
    }

    public List<ClassLoaderLeakChain> analyze(Heap heap, ClassLoaderReport baseReport,
                                              boolean compressedOops, double correctionRatio) {
        Set<String> duplicateClassLoaderNames = collectDuplicateLoaderNames(baseReport);
        List<ClassLoaderInfo> suspicious = identifySuspicious(baseReport);

        List<ClassLoaderLeakChain> chains = new ArrayList<>();
        for (ClassLoaderInfo info : suspicious) {
            List<GCRootPath> paths = pathToGCRootAnalyzer.findPaths(
                    heap, info.objectId(), true, MAX_PATHS_PER_LOADER, compressedOops, correctionRatio);

            GCRootPath path = paths.isEmpty() ? null : paths.get(0);
            List<CauseHint> hints = path == null ? List.of() : detectHints(path);
            boolean hasDuplicates = duplicateClassLoaderNames.contains(info.classLoaderClassName());

            chains.add(new ClassLoaderLeakChain(
                    info.objectId(),
                    info.classLoaderClassName(),
                    info.classCount(),
                    info.totalClassSize(),
                    info.retainedSize(),
                    path,
                    hints,
                    hasDuplicates));
        }

        chains.sort(Comparator.comparingLong(ClassLoaderLeakChain::retainedSize).reversed());

        LOG.info("Class loader leak chain analysis complete: suspicious={} chainsWithPath={} chainsWithHints={}",
                chains.size(),
                chains.stream().filter(c -> c.gcRootPath() != null).count(),
                chains.stream().filter(c -> !c.causeHints().isEmpty()).count());

        return chains;
    }

    private static List<ClassLoaderInfo> identifySuspicious(ClassLoaderReport baseReport) {
        Set<Long> selectedIds = new LinkedHashSet<>();
        List<ClassLoaderInfo> result = new ArrayList<>();

        for (ClassLoaderInfo info : baseReport.classLoaders()) {
            if (info.objectId() == 0L) {
                continue;
            }
            boolean qualifies = info.retainedSize() >= LARGE_LOADER_BYTES
                    || isWebappOrUrlLoader(info.classLoaderClassName());
            if (qualifies && selectedIds.add(info.objectId())) {
                result.add(info);
                if (result.size() >= MAX_LOADERS_TO_CHECK) {
                    break;
                }
            }
        }
        return result;
    }

    private static Set<String> collectDuplicateLoaderNames(ClassLoaderReport baseReport) {
        Set<String> names = new HashSet<>();
        for (DuplicateClassInfo dup : baseReport.duplicateClasses()) {
            names.addAll(dup.classLoaderNames());
        }
        return names;
    }

    private static boolean isWebappOrUrlLoader(String className) {
        if (className == null) {
            return false;
        }
        return className.contains("WebappClassLoader")
                || className.contains("ParallelWebappClassLoader")
                || className.equals("java.net.URLClassLoader")
                || className.contains("OsgiBundleClassLoader");
    }

    static List<CauseHint> detectHints(GCRootPath path) {
        List<CauseHint> hints = new ArrayList<>();

        String rootType = path.rootType();
        if (rootType != null && rootType.toLowerCase().contains("jni")) {
            hints.add(new CauseHint(HintKind.JNI_GLOBAL, "JNI " + rootType, -1L));
        }

        for (PathStep step : path.steps()) {
            String className = step.className();
            String fieldName = step.fieldName();

            if (className != null) {
                if (className.equals("java.lang.ThreadLocal")
                        || className.equals("java.lang.InheritableThreadLocal")
                        || className.endsWith("ThreadLocalMap")
                        || className.endsWith("ThreadLocalMap$Entry")) {
                    hints.add(new CauseHint(HintKind.THREAD_LOCAL,
                            "ThreadLocal entry: " + className, step.objectId()));
                }
                if (className.equals("java.sql.DriverManager")
                        || className.contains(".jdbc.Driver")) {
                    hints.add(new CauseHint(HintKind.JDBC_DRIVER,
                            "JDBC driver registration: " + className, step.objectId()));
                }
                if (className.equals("java.util.ServiceLoader")
                        || className.contains("ServiceLoader$LazyClassPathLookupIterator")
                        || className.contains("ServiceLoader$Provider")) {
                    hints.add(new CauseHint(HintKind.SERVICE_LOADER,
                            "ServiceLoader cache: " + className, step.objectId()));
                }
                if (className.equals("java.util.logging.LogManager")
                        || className.equals("java.util.logging.Logger")
                        || className.endsWith(".LoggerFactory")
                        || className.endsWith(".LogManager")) {
                    hints.add(new CauseHint(HintKind.LOGGER,
                            "Logger / LogManager: " + className, step.objectId()));
                }
            }
            if (fieldName != null) {
                if (fieldName.equals("contextClassLoader")) {
                    hints.add(new CauseHint(HintKind.CONTEXT_CLASSLOADER,
                            "Thread.contextClassLoader", step.objectId()));
                }
                if (fieldName.equals("threadLocals") || fieldName.equals("inheritableThreadLocals")) {
                    hints.add(new CauseHint(HintKind.THREAD_LOCAL,
                            "Thread." + fieldName, step.objectId()));
                }
            }
        }
        return hints;
    }
}
