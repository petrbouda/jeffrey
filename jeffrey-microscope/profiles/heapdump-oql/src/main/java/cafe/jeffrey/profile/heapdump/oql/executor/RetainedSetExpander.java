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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JavaClassRow;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Post-pass for {@code AS RETAINED SET}: expands a seed set of instance IDs
 * (the result of running the inner SELECT) to the union of every instance
 * dominated by any seed — i.e. the bytes that would be freed if all seeds
 * became unreachable.
 *
 * <p>Walks down the {@code dominator} table in one recursive query, capped by
 * {@link #MAX_RETAINED_SET_SIZE} so a pathological scan over the whole heap can't
 * hang the executor: no further level is started once the cap is reached. The
 * expansion is answered seeds first, then level by level.
 */
public final class RetainedSetExpander {

    private static final int MAX_RETAINED_SET_SIZE = 200_000;

    private static final String SEED_ARRAY_TYPE = "BIGINT";

    /**
     * Every instance dominated by a seed, the seeds included. {@code USING KEY} keeps one row per
     * instance, so two seeds dominating each other expand once. The Java walk this replaced queried
     * {@code dominator_id} once per instance; with no index on that column -- the table is stored
     * sorted by it instead -- that was a pruned scan per instance, 19 s for nine HashMaps on a
     * 144 MB dump against 40 ms for this, on DuckDB 2.0's recursive CTEs.
     */
    private static final String RETAINED_SET_SQL = """
            WITH RECURSIVE retained(instance_id, depth) USING KEY (instance_id) AS (
                SELECT UNNEST(?::BIGINT[]), 0
                UNION
                SELECT d.instance_id, min(r.depth) + 1
                FROM retained r
                JOIN dominator d ON d.dominator_id = r.instance_id
                WHERE d.instance_id NOT IN (SELECT instance_id FROM recurring.retained)
                  AND (SELECT count(*) FROM recurring.retained) < ?
                GROUP BY d.instance_id
            )
            SELECT instance_id
            FROM retained
            ORDER BY depth, instance_id
            LIMIT ?
            """;

    private RetainedSetExpander() {
    }

    public static OQLQueryResult expand(OQLQueryResult innerResult, HeapView view, int limit) throws SQLException {
        if (innerResult.errorMessage() != null) {
            return innerResult;
        }
        Set<Long> seeds = new LinkedHashSet<>();
        for (OQLResultEntry e : innerResult.results()) {
            if (e.objectId() != null) {
                seeds.add(e.objectId());
            }
        }
        List<Long> retained = new ArrayList<>();
        if (!seeds.isEmpty()) {
            Connection conn = view.databaseClient().connection();
            Array seedArray = conn.createArrayOf(SEED_ARRAY_TYPE, seeds.toArray());
            try (PreparedStatement stmt = conn.prepareStatement(RETAINED_SET_SQL)) {
                stmt.setArray(1, seedArray);
                stmt.setInt(2, MAX_RETAINED_SET_SIZE);
                stmt.setInt(3, MAX_RETAINED_SET_SIZE);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        retained.add(rs.getLong(1));
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
            if (inst == null) {
                continue;
            }
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
