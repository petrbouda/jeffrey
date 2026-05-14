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

import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;

/**
 * A pre-computed, on-disk-cached heap-dump analysis. Closed set — every cached
 * analysis the manager knows about is one of the permitted implementations.
 *
 * <p>Each implementation declares:
 * <ul>
 *     <li>{@link #fileName()} — the JSON sidecar file under the profile's heap-dump analysis directory</li>
 *     <li>{@link #type()} — the report type for typed deserialization</li>
 *     <li>{@link #needsDominatorTree()} — whether the dominator tree must be present before {@link #compute}</li>
 *     <li>{@link #displayName()} — human-readable name used in logs and error messages</li>
 *     <li>{@link #compute} — runs the analysis against an open {@link HeapView}</li>
 * </ul>
 */
public sealed interface CachedAnalysis<T> permits
        LeakSuspectsAnalysis,
        StringHeapAnalysis,
        ThreadHeapAnalysis,
        CollectionHeapAnalysis,
        BiggestObjectsAnalysis,
        BiggestCollectionsAnalysis,
        ClassLoaderHeapAnalysis,
        ConsumerReportAnalysis {

    String fileName();

    Class<T> type();

    boolean needsDominatorTree();

    String displayName();

    T compute(HeapView view) throws SQLException;
}
