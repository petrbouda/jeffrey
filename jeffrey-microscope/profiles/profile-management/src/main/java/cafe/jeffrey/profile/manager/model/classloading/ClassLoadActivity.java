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

package cafe.jeffrey.profile.manager.model.classloading;

import java.util.List;

/**
 * Aggregated per-class load activity, derived from {@code jdk.ClassLoad} events. These events are
 * disabled by default (high overhead), so {@link #empty()} is returned when none are present.
 *
 * @param totalCount total number of {@code jdk.ClassLoad} events in the recording
 * @param slowest    the slowest individual class loads, ordered by descending duration
 */
public record ClassLoadActivity(long totalCount, List<ClassLoadEntry> slowest) {

    public static ClassLoadActivity empty() {
        return new ClassLoadActivity(0, List.of());
    }
}
