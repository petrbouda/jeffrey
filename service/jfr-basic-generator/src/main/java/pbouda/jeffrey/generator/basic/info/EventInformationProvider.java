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

package pbouda.jeffrey.generator.basic.info;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.event.AllEventsCollector;
import pbouda.jeffrey.generator.basic.event.AllEventsProcessor;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EventInformationProvider implements Supplier<List<EventSummary>> {

    private final List<Path> recordings;
    private final CompositeExtraInfoEnhancer extraInfoEnhancer;
    private final ProcessableEvents processableEvents;

    public EventInformationProvider(List<Path> recordings) {
        this(recordings, ProcessableEvents.all());
    }

    public EventInformationProvider(List<Path> recordings, List<Type> supportedEvents) {
        this(recordings, new ProcessableEvents(supportedEvents));
    }

    public EventInformationProvider(List<Path> recordings, ProcessableEvents processableEvents) {
        this.recordings = recordings;
        this.extraInfoEnhancer = new CompositeExtraInfoEnhancer(recordings.getFirst());
        this.processableEvents = processableEvents;
        this.extraInfoEnhancer.initialize();
    }

    @Override
    public List<EventSummary> get() {
        List<EventSummary> eventSummaries = RecordingIterators.automaticAndCollect(
                recordings,
                () -> new AllEventsProcessor(processableEvents),
                new AllEventsCollector());

        return eventSummaries.stream()
                .map(extraInfoEnhancer)
                .toList();
    }
}
