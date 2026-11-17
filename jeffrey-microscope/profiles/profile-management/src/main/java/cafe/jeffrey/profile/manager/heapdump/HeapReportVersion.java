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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.manager.heapdump.analysis.CachedAnalysis;

import java.sql.SQLException;

/** The report algorithm and the actual input tree used to compute a cached report. */
public record HeapReportVersion(int analysisVersion, Long treeVersion) {

    public HeapReportVersion {
        if (analysisVersion <= 0 || (treeVersion != null && treeVersion <= 0)) {
            throw new IllegalArgumentException("Report versions must be positive");
        }
    }

    public static HeapReportVersion expected(CachedAnalysis<?> analysis) {
        return new HeapReportVersion(analysis.analysisVersion(),
                analysis.dependsOnDominatorTree() ? (long) DominatorTreeBuilder.TREE_VERSION : null);
    }

    public static HeapReportVersion from(CachedAnalysis<?> analysis, HeapView view) throws SQLException {
        if (analysis.readsTreeWhenBuilt()) {
            // It read the tree only if a current one was there; an obsolete one it did not read at all.
            return view.hasDominatorTree() ? expected(analysis) : withoutTree(analysis);
        }
        return new HeapReportVersion(analysis.analysisVersion(),
                analysis.dependsOnDominatorTree() ? view.dominatorTreeVersion() : null);
    }

    /** The version of a report that reads the tree when built, computed while none was. */
    public static HeapReportVersion withoutTree(CachedAnalysis<?> analysis) {
        return new HeapReportVersion(analysis.analysisVersion(), null);
    }
}
