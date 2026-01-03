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

import java.time.Duration;

public record UndefinedTimeRange() implements TimeRange {

    public static final UndefinedTimeRange INSTANCE = new UndefinedTimeRange();

    @Override
    public RelativeTimeRange toRelativeTimeRange(ProfilingStartEnd profilingStartEnd) {
        return new RelativeTimeRange(
                Duration.ZERO,
                Duration.between(profilingStartEnd.start(), profilingStartEnd.end()));
    }
}
