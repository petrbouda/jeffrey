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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plan B pre-pass: collects every {@code class_id} whose hierarchy descends
 * from {@code rootClassName}. Walks the {@code super_class_id} chain in
 * DuckDB iteratively (cheap because the index on {@code super_class_id} is
 * already in place).
 *
 * <p>IMPLEMENTS support requires a {@code class_interface} table that doesn't
 * exist yet in the schema; we throw {@link UnsupportedOperationException}
 * until Phase 4 adds it.
 */
public final class ClassHierarchyResolver {

    private static final String SUBCLASS_SQL =
            "SELECT class_id FROM class WHERE super_class_id IN (SELECT class_id FROM class WHERE class_id = ANY (?))";

    /**
     * Returns the set of {@code class_id}s that an INSTANCEOF query should
     * scan. Includes the root class plus every descendant.
     */
    public List<Long> resolveInstanceOf(HeapView view, String rootClassName) throws SQLException {
        List<JavaClassRow> rootMatches = view.findClassesByName(rootClassName);
        if (rootMatches.isEmpty()) {
            return List.of();
        }
        Set<Long> visited = new HashSet<>();
        Deque<Long> frontier = new ArrayDeque<>();
        for (JavaClassRow row : rootMatches) {
            visited.add(row.classId());
            frontier.add(row.classId());
        }
        Connection conn = view.connection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT class_id FROM class WHERE super_class_id = ?")) {
            while (!frontier.isEmpty()) {
                long parentId = frontier.poll();
                stmt.setLong(1, parentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long childId = rs.getLong(1);
                        if (visited.add(childId)) {
                            frontier.add(childId);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(visited);
    }

    /**
     * Returns every {@code class_id} that directly or transitively implements
     * the named interface. Walks the {@code class_interface} table and then
     * folds in the {@code super_class_id} chain so subclasses of an
     * implementing class also qualify.
     *
     * <p>Standard HPROF dumps don't carry interface info — the
     * {@code class_interface} table is empty for those indexes, and the
     * method returns an empty list (no error). Indexes built from extended
     * dump formats that populate the table will get the real walk.
     */
    public List<Long> resolveImplements(HeapView view, String interfaceName) throws SQLException {
        List<JavaClassRow> ifaceMatches = view.findClassesByName(interfaceName);
        if (ifaceMatches.isEmpty()) {
            return List.of();
        }
        Set<Long> directlyImplementing = new HashSet<>();
        Connection conn = view.connection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT class_id FROM class_interface WHERE interface_class_id = ?")) {
            for (JavaClassRow row : ifaceMatches) {
                stmt.setLong(1, row.classId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        directlyImplementing.add(rs.getLong(1));
                    }
                }
            }
        }
        if (directlyImplementing.isEmpty()) {
            return List.of();
        }
        // Fold in subclass closure: any subclass of an implementing class
        // inherits the interface contract.
        Set<Long> visited = new HashSet<>(directlyImplementing);
        Deque<Long> frontier = new ArrayDeque<>(directlyImplementing);
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT class_id FROM class WHERE super_class_id = ?")) {
            while (!frontier.isEmpty()) {
                long parentId = frontier.poll();
                stmt.setLong(1, parentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long childId = rs.getLong(1);
                        if (visited.add(childId)) {
                            frontier.add(childId);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(visited);
    }
}
