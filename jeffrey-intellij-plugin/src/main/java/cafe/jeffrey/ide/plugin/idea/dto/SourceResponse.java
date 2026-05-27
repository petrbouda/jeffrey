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
 * Result of {@code GET /api/jeffrey/source}: the raw source text of a class, for display inside
 * Microscope's source viewer. {@code decompiled} is true when the text comes from a decompiled
 * library class (no sources attached).
 */
public record SourceResponse(
        boolean resolved,
        @Nullable String content,
        @Nullable String file,
        boolean decompiled,
        @Nullable String reason
) {

    public static SourceResponse notResolved(String reason) {
        return new SourceResponse(false, null, null, false, reason);
    }
}
