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

/**
 * Response for {@code POST /heap/initialize}: the freshly-computed
 * {@link HeapSummary} plus the per-phase timings from the index build that
 * just ran. {@code subPhases} is the empty list when an existing index was
 * reused instead of rebuilt.
 *
 * @param summary    heap-level totals derived from the index
 * @param subPhases  per-phase breakdown of {@link HprofIndex#build} for the
 *                   UI's "Building indexes" accordion; empty when no rebuild ran
 */
public record InitializeResult(
        HeapSummary summary,
        List<SubPhaseTiming> subPhases
) {
}
