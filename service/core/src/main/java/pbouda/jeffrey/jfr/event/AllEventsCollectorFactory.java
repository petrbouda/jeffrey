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

package pbouda.jeffrey.jfr.event;

import org.eclipse.collections.api.map.MutableMap;
import pbouda.jeffrey.jfrparser.jdk.CollectorFactory;

import java.util.List;
import java.util.stream.Collector;

public class AllEventsCollectorFactory implements CollectorFactory<
        MutableMap<String, EventTypeCollector>, List<EventSummary>> {

    @Override
    public Collector<MutableMap<String, EventTypeCollector>, ?, List<EventSummary>> single() {
        return new AllEventsCollector();
    }

    @Override
    public Collector<MutableMap<String, EventTypeCollector>, ?, List<EventSummary>> merging() {
        return new AllEventsCollector();
    }
}
