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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.SQLException;
import java.time.Instant;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.parser.DumpMetadata;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

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
