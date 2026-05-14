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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;
import java.util.List;

/**
 * Runs {@link ThreadAnalyzer} and derives summary counts (daemon, user, total
 * retained) into the final report. Does not request the dominator tree itself
 * — the init pipeline runs the dominator step before this analysis, and the
 * analyzer reads retained sizes from the persisted {@code retained_size}
 * table; if absent, retained sizes come back {@code null} which the analyzer
 * handles gracefully.
 */
public final class ThreadHeapAnalysis implements CachedAnalysis<ThreadAnalysisReport> {

    private static final String FILE_NAME = "thread-analysis.json";

    private static final String DISPLAY_NAME = "Thread analysis";

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<ThreadAnalysisReport> type() {
        return ThreadAnalysisReport.class;
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
    public ThreadAnalysisReport compute(HeapView view) throws SQLException {
        List<HeapThreadInfo> threads = ThreadAnalyzer.analyze(view);
        int daemonCount = (int) threads.stream().filter(HeapThreadInfo::daemon).count();
        int userCount = threads.size() - daemonCount;
        long totalRetained = threads.stream()
                .mapToLong(t -> t.retainedSize() != null ? t.retainedSize() : 0L)
                .sum();
        return new ThreadAnalysisReport(threads.size(), daemonCount, userCount, totalRetained, threads);
    }
}
