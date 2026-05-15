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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.StringAnalyzer;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;
import java.util.List;

public final class StringHeapAnalysis implements CachedAnalysis<StringAnalysisReport> {

    private static final String FILE_NAME = "string-analysis.json";

    private static final String DISPLAY_NAME = "String analysis";

    private final int topN;

    private final List<JvmStringFlag> jvmFlags;

    public StringHeapAnalysis(int topN, List<JvmStringFlag> jvmFlags) {
        this.topN = topN;
        this.jvmFlags = jvmFlags;
    }

    public StringHeapAnalysis() {
        this(0, List.of());
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<StringAnalysisReport> type() {
        return StringAnalysisReport.class;
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
    public StringAnalysisReport compute(HeapView view) throws SQLException {
        StringAnalysisReport base = StringAnalyzer.analyze(view, topN);
        return new StringAnalysisReport(
                base.totalStrings(),
                base.totalStringShallowSize(),
                base.uniqueArrays(),
                base.sharedArrays(),
                base.totalSharedStrings(),
                base.memorySavedByDedup(),
                base.potentialSavings(),
                base.topByRetained(),
                base.topInstancesByRetained(),
                base.alreadyDeduplicated(),
                base.opportunities(),
                jvmFlags);
    }
}
