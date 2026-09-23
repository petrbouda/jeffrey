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

import cafe.jeffrey.profile.common.pipeline.SubPhaseTiming;

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
