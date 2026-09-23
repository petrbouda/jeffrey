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

package cafe.jeffrey.profile.manager.model.span;

/**
 * A single span across all tags, used to drive the profile-wide slowest-spans list.
 * Unlike {@link SpanDetailRow} it carries the {@code tag}, since the list spans every tag.
 *
 * @param startEpochMillis absolute UTC epoch-millis start
 * @param durationNanos    span duration in nanoseconds
 * @param threadHash       thread-identity hash — pairing key for the span's events (works for
 *                         platform and virtual threads). A {@code String} because the 64-bit hash
 *                         exceeds JavaScript's safe-integer range and would lose precision if sent
 *                         as a JSON number.
 * @param threadName       thread name (may be {@code null})
 * @param isVirtual        whether the thread is a virtual thread
 * @param tag              span tag (empty string for no tag)
 */
public record SpanSlowestRow(
        long startEpochMillis,
        long durationNanos,
        String threadHash,
        String threadName,
        boolean isVirtual,
        String tag) {
}
