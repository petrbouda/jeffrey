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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ConsumerReportAnalyzer;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;

public final class ConsumerReportAnalysis implements CachedAnalysis<ConsumerReport> {

    private static final String FILE_NAME = "consumer-report.json";

    private static final String DISPLAY_NAME = "Consumer report";

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<ConsumerReport> type() {
        return ConsumerReport.class;
    }

    @Override
    public boolean needsDominatorTree() {
        return true;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public ConsumerReport compute(HeapView view) throws SQLException {
        return ConsumerReportAnalyzer.analyze(view);
    }
}
