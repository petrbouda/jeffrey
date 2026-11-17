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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.CollectionAnalyzer;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.SQLException;

public final class CollectionHeapAnalysis implements CachedAnalysis<CollectionAnalysisReport> {

    private static final String FILE_NAME = "collection-analysis.json";

    private static final String DISPLAY_NAME = "Collection analysis";

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<CollectionAnalysisReport> type() {
        return CollectionAnalysisReport.class;
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
    public CollectionAnalysisReport compute(HeapView view) throws SQLException {
        return CollectionAnalyzer.analyze(view);
    }
}
