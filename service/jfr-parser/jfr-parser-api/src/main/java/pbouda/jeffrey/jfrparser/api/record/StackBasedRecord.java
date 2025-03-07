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

package pbouda.jeffrey.jfrparser.api.record;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.api.type.JfrClass;
import pbouda.jeffrey.jfrparser.api.type.JfrEventType;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;

import java.time.Duration;
import java.time.Instant;

public sealed interface StackBasedRecord permits SimpleRecord {

    /**
     * The type of the event that was recorded.
     *
     * @return the type of the event.
     */
    Type type();

    /**
     * The type of the event that was recorded.
     *
     * @return the type of the event.
     */
    Instant timestamp();

    /**
     * The time difference between the start of the recording and the time of the event.
     *
     * @return duration from the start of the recording.
     */
    Duration timestampFromStart();

    /**
     * One record can represent multiple samples to optimize the memory footprint and processing.
     *
     * @return number of samples represented by this record.
     */
    long samples();

    /**
     * Defines the weight of the single sample.
     * It can be 1 in case of Execution Sample, but it can be more in case of Allocation Sample
     *
     * @return the value of the weight for this record.
     */
    long sampleWeight();

    /**
     * The type of the event that was recorded.
     *
     * @return the type of the event.
     */
    JfrEventType eventType();

    /**
     * The stacktrace of the thread at the time of recording the sample.
     *
     * @return stack trace of the sample
     */
    JfrStackTrace stackTrace();

    /**
     * The thread that recorded the sample.
     *
     * @return active thread at the time of recording.
     */
    JfrThread thread();

    /**
     * The entity that is responsible for the weight of the sample.
     * It can be a class in case of Allocation Sample.
     *
     * @return the entity that is responsible for the weight of the sample.
     */
    JfrClass weightEntity();
}
