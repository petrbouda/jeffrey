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
import cafe.jeffrey.profile.heapdump.oql.compiler.ClassHierarchyResolver;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.HybridPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.PrePass.ClassHierarchyExpansion;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompiler;
import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes a Plan B query: run the pre-pass to gather the IN-list values,
 * substitute them into the SQL plan's placeholder, then delegate to
 * {@link SqlExecutor}.
 */
public final class HybridExecutor {

    private static final ClassHierarchyResolver RESOLVER = new ClassHierarchyResolver();

    private HybridExecutor() {
    }

    public static OQLQueryResult execute(HybridPlan plan, HeapView view, int limit) throws SQLException {
        ClassHierarchyExpansion expansion = (ClassHierarchyExpansion) plan.prePass();
        List<Long> classIds = expansion.isInterface()
                ? RESOLVER.resolveImplements(view, expansion.rootClassName())
                : RESOLVER.resolveInstanceOf(view, expansion.rootClassName());
        if (classIds.isEmpty()) {
            return OQLQueryResult.success(List.of(), 0, false, 0);
        }
        SqlPlan source = plan.sqlPlan();
        String placeholder = OqlCompiler.classIdsPlaceholder();
        String classIdList = classIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String substituted = source.sql().replace(placeholder, classIdList);
        SqlPlan effective = new SqlPlan(substituted, new ArrayList<>(source.params()),
                source.needsDominatorTree(), source.resultShape());
        return SqlExecutor.execute(effective, view, limit);
    }
}
