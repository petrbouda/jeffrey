/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.DominatedClassEntry;
import cafe.jeffrey.profile.heapdump.view.HeapView;

/**
 * Where a leak suspect's memory accumulates, and what it is made of — read from the dominator tree,
 * so the answer that names a suspect also names its contents rather than leaving the walk down to
 * them to the caller.
 *
 * <p>The <em>accumulation point</em> is found by descending from the suspect into its largest
 * dominated child for as long as that child still retains {@link #ACCUMULATION_SHARE} of the
 * suspect: a holder → its {@code ArrayList} → the list's {@code Object[]}, which is where the
 * elements hang. The descent stops after {@link #MAX_DESCENT} levels, so a long linked structure
 * names the deepest level reached rather than walking it whole.
 *
 * <p>The <em>contents</em> are the objects the accumulation point directly dominates, grouped by
 * class: how many, and how much of the suspect they retain.
 */
public final class SuspectClusters {

    /** A dominated child still part of the accumulation chain retains at least this share of the suspect. */
    public static final double ACCUMULATION_SHARE = 0.8;

    /** Levels the descent walks at most before it names the deepest one reached. */
    public static final int MAX_DESCENT = 64;

    /** Classes kept in a suspect's contents, largest first; the cluster's class count says how many there were. */
    public static final int CONTENT_CLASSES = 20;

    private static final String UNKNOWN_CLASS = "<unknown>";

    /** The largest object {@code ?} directly dominates, with its retained size and class. */
    private static final String LARGEST_CHILD_SQL = """
            SELECT d.instance_id, r.bytes, c.name
            FROM dominator d
            JOIN retained_size r ON r.instance_id = d.instance_id
            LEFT JOIN instance i ON i.instance_id = d.instance_id
            LEFT JOIN class c ON c.class_id = i.class_id
            WHERE d.dominator_id = ?
            ORDER BY r.bytes DESC, d.instance_id
            LIMIT 1
            """;

    /** The objects {@code ?} directly dominates, grouped by class, largest retained total first. */
    private static final String CONTENTS_SQL = """
            SELECT COALESCE(c.name, '%s') AS class_name, COUNT(*) AS objects, SUM(r.bytes) AS retained
            FROM dominator d
            JOIN retained_size r ON r.instance_id = d.instance_id
            LEFT JOIN instance i ON i.instance_id = d.instance_id
            LEFT JOIN class c ON c.class_id = i.class_id
            WHERE d.dominator_id = ?
            GROUP BY class_name
            ORDER BY retained DESC, class_name
            """.formatted(UNKNOWN_CLASS);

    /**
     * The accumulation point of one suspect and the classes it holds.
     *
     * @param objectId      the accumulation point; the suspect itself when no child holds most of it
     * @param className     its class
     * @param depth         levels below the suspect; 0 is the suspect
     * @param descentCapped whether the descent stopped at {@link #MAX_DESCENT} rather than at the point
     * @param objects       how many objects it directly dominates
     * @param heldBytes     what those objects retain together
     * @param contents      the classes they belong to, largest first, at most {@link #CONTENT_CLASSES}
     * @param classCount    how many classes they belong to, before the cut
     */
    public record Cluster(
            long objectId,
            String className,
            int depth,
            boolean descentCapped,
            long objects,
            long heldBytes,
            List<DominatedClassEntry> contents,
            int classCount) {

        public Cluster {
            if (depth < 0) {
                throw new IllegalArgumentException("depth must not be negative: depth=" + depth);
            }
            contents = List.copyOf(contents);
        }
    }

    private record Child(long objectId, long retained, String className) {
    }

    private SuspectClusters() {
    }

    /**
     * The cluster of the suspect {@code suspectId}, which retains {@code suspectRetained} bytes and is
     * of class {@code suspectClass}.
     */
    public static Cluster of(HeapView view, long suspectId, String suspectClass, long suspectRetained)
            throws SQLException {
        long floor = (long) Math.ceil(suspectRetained * ACCUMULATION_SHARE);
        long pointId = suspectId;
        String pointClass = suspectClass;
        int depth = 0;
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(LARGEST_CHILD_SQL)) {
            while (depth < MAX_DESCENT) {
                Optional<Child> child = largestChild(stmt, pointId);
                if (child.isEmpty() || child.get().retained() < floor) {
                    break;
                }
                pointId = child.get().objectId();
                pointClass = child.get().className();
                depth++;
            }
        }
        boolean capped = depth == MAX_DESCENT;
        return contents(view, pointId, pointClass, depth, capped, suspectRetained);
    }

    private static Optional<Child> largestChild(PreparedStatement stmt, long parentId) throws SQLException {
        stmt.setLong(1, parentId);
        try (ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            String className = rs.getString(3);
            return Optional.of(new Child(rs.getLong(1), rs.getLong(2), className == null ? UNKNOWN_CLASS : className));
        }
    }

    private static Cluster contents(
            HeapView view, long pointId, String pointClass, int depth, boolean capped, long suspectRetained)
            throws SQLException {
        List<DominatedClassEntry> kept = new ArrayList<>();
        long objects = 0;
        long held = 0;
        int classes = 0;
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(CONTENTS_SQL)) {
            stmt.setLong(1, pointId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long count = rs.getLong(2);
                    long retained = rs.getLong(3);
                    objects += count;
                    held += retained;
                    classes++;
                    if (kept.size() < CONTENT_CLASSES) {
                        double share = suspectRetained == 0 ? 0.0 : (retained * 100.0) / suspectRetained;
                        kept.add(new DominatedClassEntry(
                                rs.getString(1), (int) Math.min(count, Integer.MAX_VALUE), retained, share));
                    }
                }
            }
        }
        return new Cluster(pointId, pointClass, depth, capped, objects, held, kept, classes);
    }
}
