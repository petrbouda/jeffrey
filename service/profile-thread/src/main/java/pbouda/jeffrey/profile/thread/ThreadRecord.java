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

package pbouda.jeffrey.profile.thread;

import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record ThreadRecord(
        JfrThread threadInfo,
        List<Object> values,
        Instant start,
        Instant end,
        Duration duration,
        String eventLabel,
        ThreadState state) {

    public ThreadRecord(
            JfrThread threadInfo,
            Instant start,
            String eventLabel,
            ThreadState state) {

        this(threadInfo, List.of(), start, null, null, eventLabel, state);
    }
}
