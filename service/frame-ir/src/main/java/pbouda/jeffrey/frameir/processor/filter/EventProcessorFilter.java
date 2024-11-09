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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.RecordedEvent;

import java.util.function.Predicate;

public interface EventProcessorFilter extends Predicate<RecordedEvent> {

    default EventProcessorFilter and(EventProcessorFilter other) {
        return (t) -> test(t) && other.test(t);
    }

    default EventProcessorFilter negate() {
        return (t) -> !test(t);
    }

    default EventProcessorFilter or(EventProcessorFilter other) {
        return (t) -> test(t) || other.test(t);
    }

    static EventProcessorFilter not(EventProcessorFilter target) {
        return target.negate();
    }
}
