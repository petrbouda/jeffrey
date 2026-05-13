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
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.HybridPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.JavaPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.RetainedSetPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.StringFallbackPlan;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;

/**
 * Top-level dispatch from a compiled {@link ExecutionPlan} to the right
 * plan-specific executor. {@link JavaPlan} returns a clear error pointing at
 * the Phase 3 work; the surface stays stable until the Java executor lands.
 */
public final class OqlExecutor {

    private OqlExecutor() {
    }

    public static OQLQueryResult execute(ExecutionPlan plan, HeapView view, int limit) throws SQLException {
        return switch (plan) {
            case SqlPlan p -> SqlExecutor.execute(p, view, limit);
            case HybridPlan p -> HybridExecutor.execute(p, view, limit);
            case JavaPlan p -> JavaExecutor.execute(p, view, limit);
            case StringFallbackPlan p -> StringFallbackExecutor.execute(p, view, limit);
            case RetainedSetPlan p -> {
                // Run the inner with an unlimited budget so we get the full
                // seed set; the expander applies the user's limit on the
                // post-expansion result.
                OQLQueryResult inner = execute(p.inner(), view, Integer.MAX_VALUE);
                yield RetainedSetExpander.expand(inner, view, limit);
            }
        };
    }
}
