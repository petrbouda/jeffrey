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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.PathToGCRootAnalyzer;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.view.HeapView;

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
