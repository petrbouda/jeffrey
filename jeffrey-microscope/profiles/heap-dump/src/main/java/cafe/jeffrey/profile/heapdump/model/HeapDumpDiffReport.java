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

import java.util.List;

/**
 * Class-histogram comparison of two heap dumps — the classic before/after
 * leak workflow: what grew, what appeared, what shrank between a baseline
 * dump and the current one.
 *
 * @param primarySummary   summary of the primary (current) dump
 * @param baselineSummary  summary of the baseline dump
 * @param instanceCountDelta total instance-count difference (primary - baseline)
 * @param shallowBytesDelta  total shallow-bytes difference (primary - baseline)
 * @param entries          per-class deltas ordered by absolute shallow-bytes
 *                         delta descending, capped at the requested topN
 */
public record HeapDumpDiffReport(
        HeapSummary primarySummary,
        HeapSummary baselineSummary,
        long instanceCountDelta,
        long shallowBytesDelta,
        List<ClassDiffEntry> entries
) {
}
