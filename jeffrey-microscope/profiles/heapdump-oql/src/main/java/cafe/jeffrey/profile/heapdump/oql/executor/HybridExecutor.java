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
import cafe.jeffrey.profile.heapdump.oql.compiler.ClassHierarchyResolver;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.HybridPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.PrePass.ClassHierarchyExpansion;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompiler;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

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
