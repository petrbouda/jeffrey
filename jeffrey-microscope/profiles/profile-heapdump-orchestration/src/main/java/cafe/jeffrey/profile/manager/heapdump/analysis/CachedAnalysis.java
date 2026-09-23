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

import cafe.jeffrey.profile.heapdump.view.HeapView;

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
        ConsumerReportAnalysis,
        DuplicateDataAnalysis {

    String fileName();

    Class<T> type();

    boolean needsDominatorTree();

    String displayName();

    T compute(HeapView view) throws SQLException;
}
