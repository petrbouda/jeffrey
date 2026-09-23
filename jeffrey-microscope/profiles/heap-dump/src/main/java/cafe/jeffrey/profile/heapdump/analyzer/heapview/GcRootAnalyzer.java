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
import java.util.LinkedHashMap;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.GCRootAnalyzer}.
 *
 * Runs a single GROUP BY on {@code root_kind} and maps the raw HPROF tag bytes
 * to the human-readable kind names the existing {@link GCRootSummary} model
 * uses.
 */
public final class GcRootAnalyzer {

    private GcRootAnalyzer() {
    }

    public static GCRootSummary analyze(HeapView view) throws SQLException {
        Map<String, Long> rootsByType = new LinkedHashMap<>();
        long[] totalBox = {0L};

        view.databaseClient().rawStream(
                HeapDumpStatement.GC_ROOT_SUMMARY,
                "SELECT root_kind, COUNT(*) FROM gc_root GROUP BY root_kind ORDER BY 2 DESC",
                rs -> {
                    long rows = 0;
                    while (rs.next()) {
                        int kind = rs.getInt(1);
                        long count = rs.getLong(2);
                        rootsByType.merge(HprofTag.Sub.rootKindName(kind), count, Long::sum);
                        totalBox[0] += count;
                        rows++;
                    }
                    return rows;
                });

        long total = totalBox[0];
        if (total == 0) {
            return GCRootSummary.EMPTY;
        }
        return new GCRootSummary(rootsByType, total);
    }

}
