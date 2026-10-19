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

package cafe.jeffrey.profile.manager.model.exceptions;

import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;

import java.util.List;
import java.util.Map;

/**
 * The profile's sampled throws read once: by class, by throw site, and the stacks behind the sites.
 *
 * @param types  one row per thrown class, the most thrown first
 * @param sites  one row per (class, throwing frame), the most thrown first
 * @param stacks every site's {@link ExceptionSiteStat#stacktraceId() stack}, top frame first — what
 *               a reader that walks the frames (MCP finding the caller's own frame) needs, and what
 *               the REST answers leave out
 */
public record ThrowAnalysis(
        List<ExceptionTypeStat> types,
        List<ExceptionSiteStat> sites,
        Map<String, List<ThrowFrame>> stacks) {

    public static final ThrowAnalysis EMPTY = new ThrowAnalysis(List.of(), List.of(), Map.of());

    public long throwCount() {
        return types.stream().mapToLong(ExceptionTypeStat::count).sum();
    }

    public long errorCount() {
        return types.stream().filter(ExceptionTypeStat::error).mapToLong(ExceptionTypeStat::count).sum();
    }

    public long countOf(ThrowOrigin origin) {
        return sites.stream()
                .filter(site -> site.origin() == origin)
                .mapToLong(ExceptionSiteStat::count)
                .sum();
    }

    /**
     * The stack behind a site, top frame first; empty when the site has none.
     */
    public List<ThrowFrame> stackOf(ExceptionSiteStat site) {
        if (site.stacktraceId() == null) {
            return List.of();
        }
        return stacks.getOrDefault(site.stacktraceId(), List.of());
    }
}
