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

package pbouda.jeffrey.provider.api.streamer.model;

import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

public record TimeseriesRecord(JfrStackTrace stacktrace, JfrThread thread, long second, long value) {

    public static TimeseriesRecord secondsAndValues(long second, long value) {
        return new TimeseriesRecord(null, null, second, value);
    }
}
