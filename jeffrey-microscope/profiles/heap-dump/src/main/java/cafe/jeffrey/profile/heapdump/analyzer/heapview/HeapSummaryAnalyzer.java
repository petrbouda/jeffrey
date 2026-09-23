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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.SQLException;
import java.time.Instant;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.view.DumpMetadata;
import cafe.jeffrey.profile.heapdump.view.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.HeapSummaryAnalyzer}.
 *
 * Produces the same {@link HeapSummary} record as the NetBeans-backed analyzer
 * by aggregating the index DB. Compressed-oops correction is applied at the
 * caller level since the index already stores corrected shallow sizes.
 */
public final class HeapSummaryAnalyzer {

    private HeapSummaryAnalyzer() {
    }

    public static HeapSummary analyze(HeapView view) throws SQLException {
        DumpMetadata meta = view.metadata();
        long totalBytes = view.totalShallowSize();
        long totalInstances = view.totalInstanceCount();
        int classCount = (int) Math.min(view.classCount(), Integer.MAX_VALUE);
        int gcRootCount = (int) Math.min(view.gcRootCount(), Integer.MAX_VALUE);
        return new HeapSummary(
                totalBytes,
                totalInstances,
                classCount,
                gcRootCount,
                Instant.ofEpochMilli(meta.timestampMs()));
    }
}
