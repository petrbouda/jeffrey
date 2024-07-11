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

package pbouda.jeffrey.jfr.info;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfr.event.AllEventsProvider;
import pbouda.jeffrey.jfr.event.EventSummary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<List<EventSummary>> {

    private final Path recording;
    private final CompositeExtraInfoEnhancer extraInfoEnhancer;
    private final List<Type> supportedEvents;

    public EventInformationProvider(Path recording) {
        this(recording, null);
    }

    public EventInformationProvider(Path recording, List<Type> supportedEvents) {
        this.recording = recording;
        this.extraInfoEnhancer = new CompositeExtraInfoEnhancer(recording);
        this.supportedEvents = supportedEvents;
        this.extraInfoEnhancer.initialize();
    }

    @Override
    public List<EventSummary> get() {
        List<EventSummary> results = new ArrayList<>();
        List<EventSummary> events = new AllEventsProvider(recording, supportedEvents).get();
        for (EventSummary event : events) {
            results.add(extraInfoEnhancer.apply(event));
        }
        return results;
    }
}
