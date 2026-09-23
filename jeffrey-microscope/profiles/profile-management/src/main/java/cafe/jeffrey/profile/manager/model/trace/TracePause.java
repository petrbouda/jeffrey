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

/**
 * One stop-the-world stretch drawn across the waterfall.
 * <p>
 * Positioned in absolute epoch microseconds, the same units {@code TraceSpanRow.startEpochMicros}
 * carries, so a band and a bar are laid out against one window without either side converting.
 *
 * @param category        {@code GC_PAUSE} or {@code SAFEPOINT}
 * @param label           what the pause called itself — the GC phase, the VM operation
 * @param startEpochMicros when it began, absolute
 * @param durationNanos   how long it lasted
 * @param nested          whether this breaks a longer pause down from the inside rather than being
 *                        one of its own — a levelled GC phase runs inside a collection pause. The
 *                        flag travels to the client so the waterfall can draw the breakdown on
 *                        request without a second query, and leave it out by default: five bands
 *                        over one stretch of stopped world say nothing the one band did not
 */
public record TracePause(
        String category,
        String label,
        long startEpochMicros,
        long durationNanos,
        boolean nested) {
}
