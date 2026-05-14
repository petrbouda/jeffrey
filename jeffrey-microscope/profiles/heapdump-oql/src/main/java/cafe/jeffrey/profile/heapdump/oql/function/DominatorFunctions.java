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
package cafe.jeffrey.profile.heapdump.oql.function;

import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;

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
