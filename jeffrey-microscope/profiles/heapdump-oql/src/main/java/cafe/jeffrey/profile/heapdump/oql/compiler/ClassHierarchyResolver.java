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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan B pre-pass: collects every {@code class_id} whose hierarchy descends
 * from {@code rootClassName}, or that implements an interface. Each closure
 * over the {@code super_class_id} chain is one recursive query: walked a
 * class at a time, {@code INSTANCEOF java.lang.Object} on a dump of 12 k
 * classes was 12 k queries and seconds of round trips; as one statement it is
 * a few milliseconds.
 */
public final class ClassHierarchyResolver {

    /** The named class and every class descending from it. */
    private static final String INSTANCE_OF_SQL = """
            WITH RECURSIVE subclass(class_id) AS (
                SELECT class_id FROM class WHERE name = ?
                UNION
                SELECT c.class_id FROM class c JOIN subclass s ON c.super_class_id = s.class_id
            )
            SELECT class_id FROM subclass ORDER BY class_id
            """;

    /**
     * Every class that declares the named interface, and every class descending from one: a
     * subclass of an implementing class inherits the contract.
     */
    private static final String IMPLEMENTS_SQL = """
            WITH RECURSIVE implementing(class_id) AS (
                SELECT ci.class_id
                FROM class_interface ci
                JOIN class i ON i.class_id = ci.interface_class_id
                WHERE i.name = ?
                UNION
                SELECT c.class_id FROM class c JOIN implementing s ON c.super_class_id = s.class_id
            )
            SELECT class_id FROM implementing ORDER BY class_id
            """;

    /**
     * Returns the set of {@code class_id}s that an INSTANCEOF query should
     * scan. Includes the root class plus every descendant.
     */
    public List<Long> resolveInstanceOf(HeapView view, String rootClassName) throws SQLException {
        return classIds(view, INSTANCE_OF_SQL, rootClassName);
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
        return classIds(view, IMPLEMENTS_SQL, interfaceName);
    }

    private static List<Long> classIds(HeapView view, String sql, String className) throws SQLException {
        List<Long> classIds = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setString(1, className);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    classIds.add(rs.getLong(1));
                }
            }
        }
        return classIds;
    }
}
