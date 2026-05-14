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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.CauseHint;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.HintKind;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ClassLoaderLeakChainAnalyzer}.
 *
 * For each suspicious classloader, runs the migrated {@link PathToGCRootAnalyzer}
 * to find a path back to a GC root and annotates the chain with
 * {@link CauseHint}s for the canonical Tomcat-redeploy leak patterns.
 *
 * "Suspicious" = retained size above {@link #LARGE_LOADER_BYTES} OR loader
 * class name matches a duplicate-classes signal.
 *
 * Hints generated:
 * <ul>
 *   <li>{@link HintKind#JNI_GLOBAL} when the path's GC root kind is JNI global/local</li>
 *   <li>{@link HintKind#CONTEXT_CLASSLOADER} when a step's class is {@code java.lang.Thread}</li>
 *   <li>{@link HintKind#THREAD_LOCAL} when a step's class is a ThreadLocal entry</li>
 *   <li>{@link HintKind#JDBC_DRIVER} when a step's class is a DriverManager-registered driver</li>
 *   <li>{@link HintKind#SERVICE_LOADER} when a step's class is a ServiceLoader cache</li>
 *   <li>{@link HintKind#LOGGER} when a step's class name contains {@code LogManager}</li>
 * </ul>
 *
 * Requires the dominator tree to be built so retained-size sorting is meaningful.
 */
public final class ClassLoaderLeakChainAnalyzer {

    private static final long LARGE_LOADER_BYTES = 50L * 1024 * 1024;
    private static final int MAX_LOADERS_TO_CHECK = 20;

    private ClassLoaderLeakChainAnalyzer() {
    }

    public static List<ClassLoaderLeakChain> analyze(HeapView view) throws SQLException {
        List<LoaderInfo> suspicious = identifySuspicious(view);
        if (suspicious.isEmpty()) {
            return List.of();
        }

        Set<String> duplicateLoaderNames = collectDuplicateLoaderNames(view);

        List<ClassLoaderLeakChain> chains = new ArrayList<>();
        for (LoaderInfo info : suspicious) {
            List<GCRootPath> paths = PathToGCRootAnalyzer.findPaths(view, info.objectId, true, 1);
            GCRootPath path = paths.isEmpty() ? null : paths.get(0);
            List<CauseHint> hints = path == null ? List.of() : detectHints(path);
            boolean hasDuplicates = duplicateLoaderNames.contains(info.className);

            chains.add(new ClassLoaderLeakChain(
                    info.objectId,
                    info.className,
                    info.classCount,
                    info.totalClassSize,
                    info.retained,
                    path,
                    hints,
                    hasDuplicates));
        }
        chains.sort(Comparator.comparingLong(ClassLoaderLeakChain::retainedSize).reversed());
        return chains;
    }

    private static List<LoaderInfo> identifySuspicious(HeapView view) throws SQLException {
        // Aggregate per-loader stats. We count classes and approximate per-class shallow
        // (instance_size × COUNT(*) of instances) — same approach as ClassLoaderAnalyzer.
        Map<Long, LoaderInfo> byId = new HashMap<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT c.classloader_id, c.class_id, c.instance_size, "
                             + "(SELECT COUNT(*) FROM instance i WHERE i.class_id = c.class_id) "
                             + "FROM class c WHERE c.classloader_id IS NOT NULL")) {
            while (rs.next()) {
                long loaderId = rs.getLong(1);
                int instanceSize = rs.getInt(3);
                long instanceCount = rs.getLong(4);
                LoaderInfo info = byId.computeIfAbsent(loaderId, id -> new LoaderInfo(id, "<resolving>"));
                info.classCount++;
                info.totalClassSize += (long) instanceSize * instanceCount;
            }
        }

        // Resolve loader class name + retained size for each candidate.
        boolean haveDom = view.hasDominatorTree();
        for (LoaderInfo info : byId.values()) {
            info.className = resolveLoaderClassName(view, info.objectId);
            info.retained = haveDom ? probeRetained(view, info.objectId) : 0L;
        }

        return byId.values().stream()
                .filter(li -> li.retained >= LARGE_LOADER_BYTES || isWebappLoader(li.className))
                .sorted(Comparator.<LoaderInfo>comparingLong(li -> li.retained).reversed())
                .limit(MAX_LOADERS_TO_CHECK)
                .toList();
    }

    private static boolean isWebappLoader(String name) {
        // Common patterns for redeploy-leak-prone loaders.
        return name.contains("WebappClassLoader")
                || name.contains("URLClassLoader")
                || name.contains("ParallelWebappClassLoader")
                || name.contains("RestartClassLoader");
    }

    private static Set<String> collectDuplicateLoaderNames(HeapView view) throws SQLException {
        Set<String> dup = new HashSet<>();
        // Class names appearing under more than one classloader_id.
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM class WHERE classloader_id IS NOT NULL "
                             + "GROUP BY name HAVING COUNT(DISTINCT classloader_id) > 1")) {
            while (rs.next()) {
                dup.add(rs.getString(1));
            }
        }
        return dup;
    }

    private static String resolveLoaderClassName(HeapView view, long loaderInstanceId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                "SELECT c.name FROM instance i JOIN class c ON i.class_id = c.class_id "
                        + "WHERE i.instance_id = ?")) {
            stmt.setLong(1, loaderInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "<unresolved>";
            }
        }
    }

    private static long probeRetained(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static List<CauseHint> detectHints(GCRootPath path) {
        List<CauseHint> hints = new ArrayList<>();

        String rootKind = path.rootType();
        if (rootKind != null && (rootKind.contains("JNI"))) {
            hints.add(new CauseHint(HintKind.JNI_GLOBAL,
                    "GC root kind is " + rootKind + " — native code holds a reference",
                    path.rootObjectId()));
        }

        for (PathStep step : path.steps()) {
            String cls = step.className();
            if (cls == null) {
                continue;
            }
            if ("java.lang.Thread".equals(cls)) {
                hints.add(new CauseHint(HintKind.CONTEXT_CLASSLOADER,
                        "Thread on the path may be pinning the loader via contextClassLoader",
                        step.objectId()));
            } else if (cls.contains("ThreadLocal") && cls.contains("Entry")) {
                hints.add(new CauseHint(HintKind.THREAD_LOCAL,
                        "ThreadLocal entry retaining a class loaded by the leaking loader",
                        step.objectId()));
            } else if (cls.startsWith("java.sql.") || cls.contains("DriverManager")) {
                hints.add(new CauseHint(HintKind.JDBC_DRIVER,
                        "JDBC DriverManager registration retains the loader",
                        step.objectId()));
            } else if (cls.contains("ServiceLoader")) {
                hints.add(new CauseHint(HintKind.SERVICE_LOADER,
                        "ServiceLoader cache keeps the loader alive",
                        step.objectId()));
            } else if (cls.contains("LogManager") || cls.endsWith(".Logger")) {
                hints.add(new CauseHint(HintKind.LOGGER,
                        "Static logger registration references the loader",
                        step.objectId()));
            }
        }
        return hints;
    }

    private static final class LoaderInfo {
        final long objectId;
        String className;
        int classCount;
        long totalClassSize;
        long retained;

        LoaderInfo(long objectId, String className) {
            this.objectId = objectId;
            this.className = className;
        }
    }
}
