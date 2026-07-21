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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.DuplicateArrayAnalyzer;
import cafe.jeffrey.profile.heapdump.model.DuplicateDataReport;
import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.SQLException;

public final class DuplicateDataAnalysis implements CachedAnalysis<DuplicateDataReport> {

    private static final String FILE_NAME = "duplicate-data.json";

    private static final String DISPLAY_NAME = "Duplicate data";

    private static final int DEFAULT_TOP_N = 50;

    private final int topN;

    public DuplicateDataAnalysis(int topN) {
        this.topN = topN;
    }

    public DuplicateDataAnalysis() {
        this(DEFAULT_TOP_N);
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<DuplicateDataReport> type() {
        return DuplicateDataReport.class;
    }

    @Override
    public boolean needsDominatorTree() {
        return false;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public DuplicateDataReport compute(HeapView view) throws SQLException {
        return DuplicateArrayAnalyzer.analyze(view, topN);
    }
}
