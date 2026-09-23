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

import java.util.Map;

/**
 * Summary of GC roots in the heap.
 *
 * @param rootsByType map of GC root type to count
 * @param totalRoots  total number of GC roots
 */
public record GCRootSummary(
        Map<String, Long> rootsByType,
        long totalRoots
) {
    /**
     * An empty GC root summary with no roots.
     */
    public static final GCRootSummary EMPTY = new GCRootSummary(Map.of(), 0);
}
