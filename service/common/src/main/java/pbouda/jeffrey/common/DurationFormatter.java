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

public abstract class DurationFormatter {

    public static String format(long duration) {
        String formatted = "";

        Duration d = Duration.ofNanos(duration);
        if (d.toDaysPart() > 0) {
            formatted += d.toDaysPart() + "d ";
        }
        if (d.toHoursPart() > 0) {
            formatted += d.toHoursPart() + "h ";
        }
        if (d.toMinutesPart() > 0) {
            formatted += d.toMinutesPart() + "m ";
        }
        if (d.toSecondsPart() > 0) {
            formatted += d.toSecondsPart() + "s ";
        }
        if (d.toMillisPart() > 0) {
            formatted += d.toMillisPart() + "ms ";
        }
        return formatted;
    }
}
