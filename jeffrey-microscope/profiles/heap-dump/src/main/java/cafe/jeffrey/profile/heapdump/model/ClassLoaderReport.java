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
