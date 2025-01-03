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

package pbouda.jeffrey.frameir.record;

import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

public record BlockingRecord(
        JfrStackTrace stackTrace,
        JfrThread thread,
        JfrClass blockingClass,
        long samples,
        long sampleWeight) implements StackBasedRecord {

    public BlockingRecord(
            JfrStackTrace stackTrace,
            JfrThread thread,
            JfrClass blockingClass,
            long sampleWeight) {
        this(stackTrace, thread, blockingClass, 1, sampleWeight);
    }
}
