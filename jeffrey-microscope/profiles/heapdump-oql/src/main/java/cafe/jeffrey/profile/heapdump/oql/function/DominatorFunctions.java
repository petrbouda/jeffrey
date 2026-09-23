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
package cafe.jeffrey.profile.heapdump.oql.function;

import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dominator-tree lookups. {@link #dominatorOf} returns the immediate dominator
 * of an instance (0 means rooted at the virtual root); {@link #dominators}
 * returns the direct children of an instance in the dominator tree.
 *
 * <p>Both require the dominator tree to be populated. Callers gate on
 * {@link cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan#needsDominatorTree()}.
 */
public final class DominatorFunctions {

    private static final String DOMINATED_CHILDREN_SQL =
            "SELECT instance_id FROM dominator WHERE dominator_id = ?";

    private DominatorFunctions() {
    }

    public static Optional<InstanceRow> dominatorOf(HeapView view, long instanceId) throws SQLException {
        long domId = view.dominatorOf(instanceId);
        if (domId <= 0) {
            return Optional.empty();
        }
        return view.findInstanceById(domId);
    }

    public static List<InstanceRow> dominators(HeapView view, long instanceId) throws SQLException {
        Connection conn = view.databaseClient().connection();
        try (PreparedStatement stmt = conn.prepareStatement(DOMINATED_CHILDREN_SQL)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<InstanceRow> out = new ArrayList<>();
                while (rs.next()) {
                    long childId = rs.getLong(1);
                    view.findInstanceById(childId).ifPresent(out::add);
                }
                return out;
            }
        }
    }
}
