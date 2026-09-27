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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

import cafe.jeffrey.profile.heapdump.model.SortBy;

/**
 * What {@code heap_getClassHistogram} ranks by, named as its answer reports it.
 */
public enum HistogramOrder {

    /** Total shallow bytes of the class's instances. */
    SIZE(SortBy.SIZE),
    /** Number of instances. */
    COUNT(SortBy.COUNT);

    private final SortBy sortBy;

    HistogramOrder(SortBy sortBy) {
        this.sortBy = sortBy;
    }

    /** The ordering the heap engine takes. */
    public SortBy sortBy() {
        return sortBy;
    }
}
