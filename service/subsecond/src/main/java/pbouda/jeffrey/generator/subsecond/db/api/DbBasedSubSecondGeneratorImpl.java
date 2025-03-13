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

package pbouda.jeffrey.generator.subsecond.db.api;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.generator.subsecond.db.SingleResult;
import pbouda.jeffrey.generator.subsecond.db.SubSecondCollectorUtils;
import pbouda.jeffrey.generator.subsecond.db.SubSecondConfig;
import pbouda.jeffrey.generator.subsecond.db.SubSecondRecordBuilder;
import pbouda.jeffrey.jfrparser.api.record.GenericRecord;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;

public class DbBasedSubSecondGeneratorImpl implements SubSecondGenerator {

    private final ProfileEventRepository eventRepository;

    public DbBasedSubSecondGeneratorImpl(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public JsonNode generate(SubSecondConfig config) {
        EventStreamConfigurer configurer = new EventStreamConfigurer()
                .withEventType(config.eventType())
                .withTimeRange(config.timeRange());

        EventStreamer<GenericRecord> streamer =
                eventRepository.newEventStreamerFactory()
                        .newGenericStreamer(configurer);

        SubSecondRecordBuilder recordBuilder = new SubSecondRecordBuilder(config);

        streamer.startStreaming()
                .forEach(recordBuilder::onRecord);

        SingleResult result = recordBuilder.build();
        return SubSecondCollectorUtils.finisher(result);
    }
}
