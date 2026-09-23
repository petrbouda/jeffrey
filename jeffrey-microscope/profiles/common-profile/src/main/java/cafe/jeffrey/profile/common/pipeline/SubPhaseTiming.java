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

package cafe.jeffrey.profile.common.pipeline;

/**
 * One leaf-level timing inside a pipeline stage. Surfaced to the UI so a stage that took, say, 84 s can
 * show the user which slice of that time went where (e.g. CHK fixed-point iteration vs persisting rows
 * back to DuckDB).
 *
 * @param name        machine-readable sub-phase id (e.g. {@code "chk_iter"})
 * @param durationMs  wall-clock duration of this sub-phase
 * @param note        optional free-text note shown alongside the time
 *                    (e.g. {@code "5 iterations"} for CHK); {@code null} when
 *                    the duration alone is enough
 */
public record SubPhaseTiming(
        String name,
        long durationMs,
        String note
) {
}
