/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common.model.time;

import pbouda.jeffrey.shared.common.model.ProfilingStartEnd;

public sealed interface TimeRange permits AbsoluteTimeRange, RelativeTimeRange, UndefinedTimeRange {

    RelativeTimeRange toRelativeTimeRange(ProfilingStartEnd profilingStartEnd);

    static TimeRange create(long startInMillis, long endInMillis, boolean absolute) {
        if (absolute) {
            return new AbsoluteTimeRange(startInMillis, endInMillis);
        } else {
            return new RelativeTimeRange(startInMillis, endInMillis);
        }
    }
}
