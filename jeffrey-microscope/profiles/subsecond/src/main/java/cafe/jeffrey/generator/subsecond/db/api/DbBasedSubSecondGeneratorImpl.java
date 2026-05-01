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

package cafe.jeffrey.generator.subsecond.db.api;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.generator.subsecond.db.SingleResult;
import cafe.jeffrey.generator.subsecond.db.SubSecondCollectorUtils;
import cafe.jeffrey.generator.subsecond.db.SubSecondConfig;
import cafe.jeffrey.generator.subsecond.db.SubSecondRecordBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

public class DbBasedSubSecondGeneratorImpl implements SubSecondGenerator {

    private final ProfileEventStreamRepository eventStreamRepository;

    public DbBasedSubSecondGeneratorImpl(ProfileEventStreamRepository eventStreamRepository) {
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public JsonNode generate(SubSecondConfig config) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(config.eventType())
                .withTimeRange(config.timeRange());

        long startOffsetMillis = config.timeRange() != null ? config.timeRange().start().toMillis() : 0;
        SingleResult result = eventStreamRepository.subSecondStreamer(configurer, new SubSecondRecordBuilder(startOffsetMillis));
        return SubSecondCollectorUtils.finisher(result);
    }
}
