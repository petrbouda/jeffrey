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

import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;

public class RecordsFrameIterator {

    private final Config config;
    private final ProfileEventRepository eventRepository;

    public RecordsFrameIterator(Config config, ProfileEventRepository eventRepository) {
        this.config = config;
        this.eventRepository = eventRepository;
    }

    public Frame iterate() {
        RecordBuilder<FlamegraphRecord, Frame> frameBuilder = new FrameBuilderResolver(
                config.eventType(), config.graphParameters(), false).resolve();

        GraphParameters params = config.graphParameters();

        /*
         * Create a query to the database with all the necessary parameters from the config.
         */
        EventStreamConfigurer configurer = new EventStreamConfigurer()
                .withEventType(config.eventType())
                .withTimeRange(config.timeRange())
                .filterStacktraceTypes(params.stacktraceTypes())
                .filterStacktraceTags(params.stacktraceTags())
                .withThreads(params.threadMode())
                .withSpecifiedThread(config.threadInfo());

        eventRepository.newEventStreamerFactory()
                .newFlamegraphStreamer(configurer)
                .startStreaming(frameBuilder::onRecord);

        return frameBuilder.build();
    }
}
