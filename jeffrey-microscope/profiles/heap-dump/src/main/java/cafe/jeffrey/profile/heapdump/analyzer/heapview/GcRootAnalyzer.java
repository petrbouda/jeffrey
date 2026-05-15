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
import java.util.LinkedHashMap;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
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
