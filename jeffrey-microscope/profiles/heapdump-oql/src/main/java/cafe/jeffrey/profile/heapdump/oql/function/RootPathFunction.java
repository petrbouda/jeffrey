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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.PathToGCRootAnalyzer;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;
import java.util.List;

/**
 * Wraps {@link PathToGCRootAnalyzer} for use in OQL projections — {@code root(o)}.
 * Returns the shortest path from a GC root to {@code o}, serialised as a
 * single string suitable for display in the {@code value} column of an
 * {@code OQLResultEntry}.
 */
public final class RootPathFunction {

    private static final int MAX_PATHS = 1;
    private static final boolean EXCLUDE_WEAK_REFS = true;

    private RootPathFunction() {
    }

    public static String renderRootPath(HeapView view, long instanceId) throws SQLException {
        List<GCRootPath> paths = PathToGCRootAnalyzer.findPaths(view, instanceId, EXCLUDE_WEAK_REFS, MAX_PATHS);
        if (paths.isEmpty()) {
            return null;
        }
        GCRootPath path = paths.get(0);
        StringBuilder out = new StringBuilder();
        out.append(path.rootClassName())
                .append('@').append(Long.toHexString(path.rootObjectId()))
                .append(" [").append(path.rootType()).append(']');
        path.steps().forEach(step -> out.append(" -> ").append(step.toString()));
        return out.toString();
    }
}
