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

package cafe.jeffrey.profile.manager.heapdump.analysis;

import cafe.jeffrey.profile.heapdump.analyzer.heapview.DominatorTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.SQLException;
import java.util.List;

/**
 * Top-N biggest retained objects, derived from the dominator tree's top-level
 * children. Requires the dominator tree.
 */
public final class BiggestObjectsAnalysis implements CachedAnalysis<BiggestObjectsReport> {

    private static final String FILE_NAME = "biggest-objects.json";

    private static final String DISPLAY_NAME = "Biggest objects";

    private static final long DOMINATOR_ROOT_ID = 0L;

    private final int topN;

    public BiggestObjectsAnalysis(int topN) {
        this.topN = topN;
    }

    public BiggestObjectsAnalysis() {
        this(0);
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<BiggestObjectsReport> type() {
        return BiggestObjectsReport.class;
    }

    @Override
    public boolean needsDominatorTree() {
        return true;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public BiggestObjectsReport compute(HeapView view) throws SQLException {
        DominatorTreeResponse response = DominatorTreeAnalyzer.children(view, DOMINATOR_ROOT_ID, topN);

        List<BiggestObjectEntry> entries = response.nodes().stream()
                .map(node -> new BiggestObjectEntry(
                        node.className(),
                        node.shallowSize(),
                        node.retainedSize(),
                        node.objectId()))
                .toList();

        long totalRetained = entries.stream().mapToLong(BiggestObjectEntry::retainedSize).sum();
        long totalHeapSize;
        try {
            totalHeapSize = view.totalShallowSize();
        } catch (SQLException e) {
            totalHeapSize = 0L;
        }
        return new BiggestObjectsReport(totalHeapSize, totalRetained, entries);
    }
}
