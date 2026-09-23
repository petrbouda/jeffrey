/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.microscope.model.StacktraceTag;
import cafe.jeffrey.microscope.model.StacktraceType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record EventStacktrace(StacktraceType type, List<EventFrame> frames, Set<StacktraceTag> tags) {

    public EventStacktrace(StacktraceType type, List<EventFrame> frames) {
        this(type, frames, new HashSet<>());
    }

    public void addStacktraceTags(Set<StacktraceTag> tags) {
        this.tags.addAll(tags);
    }
}
