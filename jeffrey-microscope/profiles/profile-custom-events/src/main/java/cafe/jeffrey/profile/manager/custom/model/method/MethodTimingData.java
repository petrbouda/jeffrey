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

package cafe.jeffrey.profile.manager.custom.model.method;

import java.util.List;

/**
 * What {@code jdk.MethodTiming} counted, one row per method.
 *
 * @param methods         the tallies, most-invoked first
 * @param totalInvocations summed across the methods — a scale marker for the page header, not a
 *                        meaningful quantity in itself, since the methods are unrelated
 */
public record MethodTimingData(
        List<MethodTimingStat> methods,
        long totalInvocations) {

    public static final MethodTimingData EMPTY = new MethodTimingData(List.of(), 0);
}
