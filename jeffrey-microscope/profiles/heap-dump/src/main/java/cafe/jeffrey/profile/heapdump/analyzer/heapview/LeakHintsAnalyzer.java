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
import java.util.ArrayList;
import java.util.List;
import cafe.jeffrey.profile.heapdump.model.LeakHintFinding;
import cafe.jeffrey.profile.heapdump.model.LeakHintFinding.Severity;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

/**
 * Runs a small set of heuristic rules over the GC root set and returns
 * findings ordered by severity (HIGH → MEDIUM → LOW). The goal is to give
 * users a triage list — "look here first" — on a fresh heap dump.
 *
 * <p>Each rule is implemented as a private static method that appends 0..N
 * findings to an output list. Adding a new rule is a one-line registration
 * in {@link #analyze}.
 */
public final class LeakHintsAnalyzer {

    /** Heuristic thresholds — tuned to surface signal, not every elevated value. */
    private static final long MANY_CLASS_INSTANCES_BYTES = 64L * 1024 * 1024; // 64 MB
    private static final int MANY_CLASS_INSTANCES_COUNT = 25;
    private static final int HIGH_JNI_GLOBAL_COUNT = 100;
    private static final long LARGE_STATIC_ARRAY_BYTES = 8L * 1024 * 1024; // 8 MB

    private LeakHintsAnalyzer() {
    }

    public static List<LeakHintFinding> analyze(HeapView view) throws SQLException {
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Leak Hints requires the dominator tree to be built");
        }
        List<LeakHintFinding> out = new ArrayList<>();
        ruleManyInstancesOfClassRooted(view, out);
        ruleHighJniGlobalCount(view, out);
        ruleLargeStaticArrayRooted(view, out);
        // Order by severity (HIGH first); preserve insertion order within a severity bucket.
        out.sort((a, b) -> a.severity().compareTo(b.severity()));
        return List.copyOf(out);
    }

    /**
     * Many GC-rooted instances of the same class + significant retained bytes.
     * Suggests a singleton-that-isn't-single pattern or thread-pool-scoped state
     * accumulating across requests.
     */
    private static void ruleManyInstancesOfClassRooted(HeapView view, List<LeakHintFinding> out)
            throws SQLException {
        String sql = "SELECT c.name, COUNT(*) AS root_count, SUM(COALESCE(r.bytes, 0)) AS retained_bytes "
                + "FROM gc_root g "
                + "JOIN instance i ON i.instance_id = g.instance_id "
                + "JOIN class c ON c.class_id = i.class_id "
                + "LEFT JOIN retained_size r ON r.instance_id = g.instance_id "
                + "GROUP BY c.name "
                + "HAVING COUNT(*) >= ? AND SUM(COALESCE(r.bytes, 0)) >= ? "
                + "ORDER BY retained_bytes DESC "
                + "LIMIT 10";

        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setInt(1, MANY_CLASS_INSTANCES_COUNT);
            stmt.setLong(2, MANY_CLASS_INSTANCES_BYTES);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String className = rs.getString(1);
                    long count = rs.getLong(2);
                    long retained = rs.getLong(3);
                    out.add(new LeakHintFinding(
                            Severity.HIGH,
                            "many-instances-of-class",
                            count + " rooted instances of " + className,
                            count + " instances of " + className + " are held by GC roots, "
                                    + "retaining " + humanBytes(retained) + " in total. Often a sign "
                                    + "of state-per-thread/request accumulation; sometimes legitimate "
                                    + "(framework caches). Drill into the Top Retainers tab to see "
                                    + "which root(s) are largest."));
                }
            }
        }
    }

    /**
     * JNI Global count exceeds the historically-typical threshold. JNI Global is the
     * leak-prone native-side category — missing {@code DeleteGlobalRef} on a code
     * path is the canonical native-leak shape.
     */
    private static void ruleHighJniGlobalCount(HeapView view, List<LeakHintFinding> out) throws SQLException {
        String sql = "SELECT COUNT(*) FROM gc_root WHERE root_kind = ?";
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setInt(1, HprofTag.Sub.ROOT_JNI_GLOBAL);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    if (count >= HIGH_JNI_GLOBAL_COUNT) {
                        out.add(new LeakHintFinding(
                                Severity.MEDIUM,
                                "high-jni-global-count",
                                count + " JNI Global references",
                                "Healthy apps rarely hold more than " + HIGH_JNI_GLOBAL_COUNT
                                        + " JNI Global references. Investigate which classes are "
                                        + "involved in the Native / JNI tab — a missing "
                                        + "DeleteGlobalRef on a hot code path is the typical "
                                        + "cause of growing RSS without growing Java heap."));
                    }
                }
            }
        }
    }

    /**
     * Sticky-class roots whose rooted object is a primitive or object array, retained
     * size over the threshold. Catches "huge static cache" patterns and oversized
     * preallocated buffers held in static fields.
     */
    private static void ruleLargeStaticArrayRooted(HeapView view, List<LeakHintFinding> out)
            throws SQLException {
        // Sticky-class roots reference class objects; for "array held by a static field"
        // patterns the rooted object IS an array (which is a regular instance with the
        // class of an array type). Detect by joining gc_root → instance → class with
        // is_array = true, on the Sticky Class kind.
        String sql = "SELECT c.name, COUNT(*) AS array_count, SUM(COALESCE(r.bytes, 0)) AS retained_bytes "
                + "FROM gc_root g "
                + "JOIN instance i ON i.instance_id = g.instance_id "
                + "JOIN class c ON c.class_id = i.class_id "
                + "LEFT JOIN retained_size r ON r.instance_id = g.instance_id "
                + "WHERE g.root_kind = ? AND c.is_array = true "
                + "GROUP BY c.name "
                + "HAVING SUM(COALESCE(r.bytes, 0)) >= ? "
                + "ORDER BY retained_bytes DESC "
                + "LIMIT 10";

        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setInt(1, HprofTag.Sub.ROOT_STICKY_CLASS);
            stmt.setLong(2, LARGE_STATIC_ARRAY_BYTES);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    long count = rs.getLong(2);
                    long retained = rs.getLong(3);
                    out.add(new LeakHintFinding(
                            Severity.LOW,
                            "large-static-array",
                            "Sticky " + name + " holds " + humanBytes(retained),
                            count + " " + name + " instance(s) held by Sticky Class roots, "
                                    + "retaining " + humanBytes(retained) + ". Likely intentional "
                                    + "static caches but worth confirming the sizing is what you "
                                    + "expect."));
                }
            }
        }
    }

    /** Tiny human-bytes formatter — keeps the analyzer free of any external dep. */
    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }
}
