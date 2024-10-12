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

package pbouda.jeffrey.jfr.configuration;

import pbouda.jeffrey.common.Collector;

import java.util.Optional;
import java.util.function.Supplier;

public class JsonFieldEventCollector implements Collector<JsonContent, JsonContent> {

    private static final JsonContent EMPTY = new JsonContent(null, null);

    @Override
    public Supplier<JsonContent> empty() {
        return () -> EMPTY;
    }

    @Override
    public JsonContent combiner(JsonContent p1, JsonContent p2) {
        if (isNotEmpty(p1)) {
            return p1;
        }
        if (isNotEmpty(p2)) {
            return p2;
        }
        return EMPTY;
    }

    @Override
    public JsonContent finisher(JsonContent combined) {
        return isNotEmpty(combined) ? combined : null;
    }

    private static boolean isNotEmpty(JsonContent content) {
        return content != null && content != EMPTY;
    }
}
