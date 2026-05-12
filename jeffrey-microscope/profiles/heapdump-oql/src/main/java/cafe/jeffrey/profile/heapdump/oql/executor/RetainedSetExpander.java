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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
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
 * Post-pass for {@code AS RETAINED SET}: expands a seed set of instance IDs
 * (the result of running the inner SELECT) to the union of every instance
 * dominated by any seed — i.e. the bytes that would be freed if all seeds
 * became unreachable.
 *
 * <p>Walks the {@code dominator} table iteratively via {@code dominator_id}
 * lookups, capped by {@link #MAX_RETAINED_SET_SIZE} so a pathological scan
 * over the whole heap can't hang the executor.
 */
public final class RetainedSetExpander {

    private static final int MAX_RETAINED_SET_SIZE = 200_000;

    private static final String CHILDREN_OF_DOMINATOR_SQL =
            "SELECT instance_id FROM dominator WHERE dominator_id = ?";

    private RetainedSetExpander() {
    }

    public static OQLQueryResult expand(OQLQueryResult innerResult, HeapView view, int limit) throws SQLException {
        if (innerResult.errorMessage() != null) {
            return innerResult;
        }
        Set<Long> retained = new HashSet<>();
        Deque<Long> frontier = new ArrayDeque<>();
        for (OQLResultEntry e : innerResult.results()) {
            if (e.objectId() == null) continue;
            if (retained.add(e.objectId())) {
                frontier.add(e.objectId());
            }
        }
        Connection conn = view.connection();
        try (PreparedStatement stmt = conn.prepareStatement(CHILDREN_OF_DOMINATOR_SQL)) {
            while (!frontier.isEmpty() && retained.size() < MAX_RETAINED_SET_SIZE) {
                long parentId = frontier.poll();
                stmt.setLong(1, parentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long childId = rs.getLong(1);
                        if (retained.add(childId)) {
                            frontier.add(childId);
                        }
                    }
                }
            }
        }
        List<OQLResultEntry> rows = new ArrayList<>(Math.min(retained.size(), limit));
        boolean hasMore = false;
        for (long id : retained) {
            if (rows.size() >= limit) {
                hasMore = true;
                break;
            }
            InstanceRow inst = view.findInstanceById(id).orElse(null);
            if (inst == null) continue;
            JavaClassRow clazz = inst.classId() == null
                    ? null
                    : view.findClassById(inst.classId()).orElse(null);
            rows.add(new OQLResultEntry(
                    id,
                    clazz == null ? null : clazz.name(),
                    (clazz == null ? "?" : clazz.name()) + "@" + Long.toHexString(id),
                    inst.shallowSize(),
                    view.retainedSize(id)));
        }
        return OQLQueryResult.success(rows, rows.size(), hasMore, 0);
    }
}
