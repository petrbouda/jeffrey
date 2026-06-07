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

package cafe.jeffrey.profile.manager.model.span;

/**
 * A single span across all tags, used to drive the profile-wide slowest-spans list.
 * Unlike {@link SpanDetailRow} it carries the {@code tag}, since the list spans every tag.
 *
 * @param startEpochMillis absolute UTC epoch-millis start
 * @param durationNanos    span duration in nanoseconds
 * @param osThreadId       OS thread id (0 if unknown)
 * @param threadName       thread name (may be {@code null})
 * @param tag              span tag (empty string for no tag)
 */
public record SpanSlowestRow(
        long startEpochMillis,
        long durationNanos,
        long osThreadId,
        String threadName,
        String tag) {
}
