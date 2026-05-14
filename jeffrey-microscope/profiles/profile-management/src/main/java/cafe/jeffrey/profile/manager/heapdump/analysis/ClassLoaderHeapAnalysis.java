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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderLeakChainAnalyzer;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;
import java.util.List;

public final class ClassLoaderHeapAnalysis implements CachedAnalysis<ClassLoaderReport> {

    private static final String FILE_NAME = "classloader-analysis.json";

    private static final String DISPLAY_NAME = "Class loader analysis";

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public Class<ClassLoaderReport> type() {
        return ClassLoaderReport.class;
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
    public ClassLoaderReport compute(HeapView view) throws SQLException {
        ClassLoaderReport baseReport = ClassLoaderAnalyzer.analyze(view);
        List<ClassLoaderLeakChain> leakChains = ClassLoaderLeakChainAnalyzer.analyze(view);
        return new ClassLoaderReport(
                baseReport.totalClassLoaders(),
                baseReport.totalClasses(),
                baseReport.duplicateClassCount(),
                baseReport.classLoaders(),
                baseReport.duplicateClasses(),
                leakChains);
    }
}
