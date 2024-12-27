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

import jdk.jfr.consumer.RecordedEvent;

import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

public record WeightExtractor(
        ToLongFunction<RecordedEvent> extractor,
        LongFunction<String> formatter,
        String classField) {

    public static WeightExtractor duration() {
        return duration(null);
    }

    public static WeightExtractor duration(String classField) {
        return new WeightExtractor(e -> e.getDuration().toNanos(), DurationFormatter::format, classField);
    }

    public static WeightExtractor allocation(String fieldName) {
        return allocation(fieldName, null);
    }

    public static WeightExtractor allocation(String fieldName, String classField) {
        return new WeightExtractor(e -> e.getLong(fieldName), BytesFormatter::format, classField);
    }
}
