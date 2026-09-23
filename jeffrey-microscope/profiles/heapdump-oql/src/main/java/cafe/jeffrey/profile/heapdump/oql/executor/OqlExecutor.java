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
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.HybridPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.JavaPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.RetainedSetPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.StringFallbackPlan;
import cafe.jeffrey.profile.heapdump.view.HeapView;

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
