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
package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;

/**
 * One page of a folded run's members, slowest first.
 *
 * @param members the page, ordered by duration descending, ties broken by span id so a page
 *                boundary never shows one member twice or skips one
 * @param total   how many members the run holds, across every page
 * @param hasMore whether pages remain after this one
 */
public record TraceSpanRunMembers(List<TraceSpanRow> members, long total, boolean hasMore) {

    public TraceSpanRunMembers {
        members = List.copyOf(members);
    }
}
