/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.resources.request;

import cafe.jeffrey.profile.TimeRangeRequest;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.config.GraphComponents;


public record GenerateFlamegraphRequest(
        String flamegraphName,
        Type eventType,
        TimeRangeRequest timeRange,
        String search,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        ThreadInfo threadInfo,
        // Set instead of threadInfo when the graph is opened from a collapsed timeline lane: the lane
        // has no thread of its own, and the page holds at most a slice of its members, so it names the
        // group and lets the server resolve every thread behind it.
        String threadGroup,
        GraphComponents components) {

    public boolean hasThreadGroup() {
        return threadGroup != null && !threadGroup.isBlank();
    }
}
