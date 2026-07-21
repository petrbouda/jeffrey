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

/**
 * Per-class delta between two heap dumps (primary vs baseline).
 *
 * @param className     the class both sides are keyed on
 * @param primaryCount  instance count in the primary dump (0 = class absent)
 * @param baselineCount instance count in the baseline dump (0 = class absent)
 * @param countDelta    {@code primaryCount - baselineCount}
 * @param primaryBytes  shallow bytes in the primary dump
 * @param baselineBytes shallow bytes in the baseline dump
 * @param bytesDelta    {@code primaryBytes - baselineBytes}
 */
public record ClassDiffEntry(
        String className,
        long primaryCount,
        long baselineCount,
        long countDelta,
        long primaryBytes,
        long baselineBytes,
        long bytesDelta
) {
}
