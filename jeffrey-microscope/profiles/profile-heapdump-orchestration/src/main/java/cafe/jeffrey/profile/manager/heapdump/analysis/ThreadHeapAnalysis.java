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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.ThreadAnalysisReport;
import cafe.jeffrey.profile.heapdump.view.HeapView;

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
