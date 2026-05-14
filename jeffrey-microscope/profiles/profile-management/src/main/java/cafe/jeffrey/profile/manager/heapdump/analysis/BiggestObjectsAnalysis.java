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

package cafe.jeffrey.profile.manager.heapdump.analysis;

import cafe.jeffrey.profile.heapdump.analyzer.heapview.DominatorTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

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
