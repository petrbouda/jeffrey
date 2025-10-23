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

package pbouda.jeffrey.frameir;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;

public class RecordsFrameIterator {

    private final GraphParameters graphParameters;
    private final ProfileEventRepository eventRepository;

    public RecordsFrameIterator(GraphParameters graphParameters, ProfileEventRepository eventRepository) {
        this.graphParameters = graphParameters;
        this.eventRepository = eventRepository;
    }

    public Frame iterate() {
        RecordBuilder<FlamegraphRecord, Frame> frameBuilder =
                new FrameBuilderResolver(graphParameters, false).resolve();

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(graphParameters.eventType())
                .withTimeRange(graphParameters.timeRange())
                .filterStacktraceTypes(graphParameters.stacktraceTypes())
                .filterStacktraceTags(graphParameters.stacktraceTags())
                .withThreads(graphParameters.threadMode())
                .withSpecifiedThread(graphParameters.threadInfo());

        return eventRepository.newEventStreamerFactory(configurer)
                .newFlamegraphStreamer()
                .startStreaming(frameBuilder);
    }
}
