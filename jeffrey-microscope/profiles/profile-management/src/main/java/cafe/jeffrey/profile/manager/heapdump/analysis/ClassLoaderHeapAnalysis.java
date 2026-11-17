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

import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderHierarchyAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderLeakChainAnalyzer;
import cafe.jeffrey.profile.heapdump.analyzer.heapview.ClassLoaderUnloadabilityAnalyzer;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderHierarchyEdge;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderInfo;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderUnloadability;
import cafe.jeffrey.profile.heapdump.view.HeapView;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        List<Long> loaderIds = new ArrayList<>(baseReport.classLoaders().size());
        for (ClassLoaderInfo info : baseReport.classLoaders()) {
            loaderIds.add(info.objectId());
        }

        List<ClassLoaderHierarchyEdge> hierarchyEdges = ClassLoaderHierarchyAnalyzer.analyze(view, loaderIds);
        Map<Long, ClassLoaderUnloadability> unloadability =
                ClassLoaderUnloadabilityAnalyzer.analyze(view, loaderIds, hierarchyEdges);
        List<ClassLoaderLeakChain> leakChains = ClassLoaderLeakChainAnalyzer.analyze(view);

        return new ClassLoaderReport(
                baseReport.totalClassLoaders(),
                baseReport.totalClasses(),
                baseReport.duplicateClassCount(),
                baseReport.classLoaders(),
                baseReport.duplicateClasses(),
                leakChains,
                hierarchyEdges,
                unloadability,
                baseReport.loaderTypes());
    }
}
