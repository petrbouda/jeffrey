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

package pbouda.jeffrey.generator.basic.event;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import pbouda.jeffrey.common.Collector;

import java.util.List;
import java.util.function.Supplier;

public class AllEventsCollector implements Collector<
        MutableMap<String, EventTypeCollector>, List<EventSummary>> {

    @Override
    public Supplier<MutableMap<String, EventTypeCollector>> empty() {
        return Maps.mutable::empty;
    }

    @Override
    public MutableMap<String, EventTypeCollector> combiner(
            MutableMap<String, EventTypeCollector> partial1,
            MutableMap<String, EventTypeCollector> partial2) {

        partial2.forEachKeyValue((key, value) -> {
            partial1.compute(key, (k, v) -> v == null ? value : v.merge(value));
        });
        return partial1;
    }

    @Override
    public List<EventSummary> finisher(MutableMap<String, EventTypeCollector> combined) {
        return combined.values().stream()
                .map(EventTypeCollector::buildSummary)
                .toList();
    }
}
