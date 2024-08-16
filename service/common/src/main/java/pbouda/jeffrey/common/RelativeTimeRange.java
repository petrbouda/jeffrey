/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common;

import java.time.Duration;
import java.time.Instant;

/**
 * @param start start from the beginning of the recording.
 * @param end   end from the beginning of the recording.
 */
public record RelativeTimeRange(Duration start, Duration end) implements TimeRange {

    private static final Duration UNDEFINED = Duration.ofMillis(-1);

    public RelativeTimeRange(long startInMillis, long endInMillis) {
        this(Duration.ofMillis(startInMillis), Duration.ofMillis(endInMillis));
    }

    public static RelativeTimeRange justStart(long startInMillis) {
        return new RelativeTimeRange(Duration.ofMillis(startInMillis), UNDEFINED);
    }

    public static RelativeTimeRange justEnd(long endInMillis) {
        return new RelativeTimeRange(UNDEFINED, Duration.ofMillis(endInMillis));
    }

    public AbsoluteTimeRange toAbsoluteTimeRange(Instant recordingStart) {
        if (start != UNDEFINED && end != UNDEFINED) {
            return new AbsoluteTimeRange(recordingStart.plus(start), recordingStart.plus(end));
        } else if (start != UNDEFINED) {
            return AbsoluteTimeRange.justStart(recordingStart.plus(start));
        } else if (end != UNDEFINED) {
            return AbsoluteTimeRange.justEnd(recordingStart.plus(end));
        }

        return AbsoluteTimeRange.UNLIMITED;
    }
}
