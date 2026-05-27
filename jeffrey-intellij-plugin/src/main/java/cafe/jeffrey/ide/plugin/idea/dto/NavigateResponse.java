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
