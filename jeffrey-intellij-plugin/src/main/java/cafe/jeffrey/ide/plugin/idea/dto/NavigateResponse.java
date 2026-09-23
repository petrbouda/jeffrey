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

package cafe.jeffrey.ide.plugin.idea.dto;

import org.jetbrains.annotations.Nullable;

/**
 * Result of {@code POST /api/jeffrey/navigate}. On success the IDE has navigated to and focused the
 * location; the flags are informational (never blocking on the Microscope side).
 *
 * @param source     resolution strategy: {@code JAVA_PRECISE} / {@code JAVA_LINE} /
 *                   {@code KOTLIN_LINE} / {@code KOTLIN_FALLBACK}
 * @param decompiled resolved file is inside a jar without sources attached
 * @param imprecise  landed on the class declaration rather than the requested member/line
 * @param stale      source mtime is much newer than the recording time
 */
public record NavigateResponse(
        boolean resolved,
        @Nullable String source,
        @Nullable String file,
        @Nullable Integer line,
        boolean decompiled,
        boolean imprecise,
        boolean stale,
        @Nullable String sourceMTime,
        @Nullable String reason
) {

    public static NavigateResponse notResolved(String reason) {
        return new NavigateResponse(false, null, null, null, false, false, false, null, reason);
    }
}
