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
 * retained) into the final report. It does not build the dominator tree: the
 * analyzer reads retained sizes only from a current tree and leaves them unknown
 * otherwise, so preparing the threads alone costs no tree build. The report is
 * stored with the tree it read, or with none; once a current tree exists, the
 * tree-less copy is recomputed over it on the next read (see
 * {@code CachedAnalysisRunner#read}).
 */
public final class ThreadHeapAnalysis implements CachedAnalysis<ThreadAnalysisReport> {

    private static final String FILE_NAME = "thread-analysis.json";

    private static final String DISPLAY_NAME = "Thread analysis";

    /** Computed with or without a tree; version 1 always required one. */
    private static final int ANALYSIS_VERSION = 2;

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<ThreadAnalysisReport> type() {
        return ThreadAnalysisReport.class;
    }

    @Override
    public int analysisVersion() {
        return ANALYSIS_VERSION;
    }

    @Override
    public boolean needsDominatorTree() {
        return false;
    }

    /** Each thread's retained size comes from the tree whenever a current one exists. */
    @Override
    public boolean dependsOnDominatorTree() {
        return true;
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
