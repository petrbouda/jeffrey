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

public abstract class EventProcessorFilters {

    public static Predicate<RecordedEvent> excludeIdleSamples(boolean excludeIdleSamples) {
        return new ExcludeIdleSamples(excludeIdleSamples);
    }

    public static Predicate<RecordedEvent> excludeNonJavaSamples(boolean excludeNonJavaSamples) {
        return new ExcludeNonJavaSamples(excludeNonJavaSamples);
    }

    public static Predicate<RecordedEvent> excludeNonJavaAndIdleSamples(
            boolean excludeNonJavaSamples, boolean excludeIdleSamples) {

        return excludeNonJavaSamples(excludeNonJavaSamples)
                .and(excludeIdleSamples(excludeIdleSamples));
    }
}
