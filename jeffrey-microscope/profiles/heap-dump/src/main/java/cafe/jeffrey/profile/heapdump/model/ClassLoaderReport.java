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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;
import java.util.Map;

/**
 * Report summarizing class loader analysis including class loader inventory,
 * duplicate class detection, hierarchy edges (parent → child), per-loader
 * unloadability diagnostics, loader type classification, and (optionally)
 * leak-chain diagnostics for suspicious class loaders.
 *
 * @param totalClassLoaders   total number of distinct class loaders found
 * @param totalClasses        total number of loaded classes
 * @param duplicateClassCount number of classes loaded by more than one class loader
 * @param classLoaders        list of class loader summaries sorted by class count descending
 * @param duplicateClasses    list of classes loaded by multiple class loaders sorted by loader count descending
 * @param leakChains          GC-root paths and cause-hint diagnostics for suspicious class loaders;
 *                            empty if leak-chain analysis was not run
 * @param hierarchyEdges      one entry per non-bootstrap loader pointing at its parent
 * @param unloadability       per-loader verdict on whether the loader can be GC'd
 * @param loaderTypes         coarse classification (Bootstrap / Platform / System / Web / OSGi / App / Custom)
 */
public record ClassLoaderReport(
        int totalClassLoaders,
        int totalClasses,
        int duplicateClassCount,
        List<ClassLoaderInfo> classLoaders,
        List<DuplicateClassInfo> duplicateClasses,
        List<ClassLoaderLeakChain> leakChains,
        List<ClassLoaderHierarchyEdge> hierarchyEdges,
        Map<Long, ClassLoaderUnloadability> unloadability,
        Map<Long, LoaderType> loaderTypes
) {
}
