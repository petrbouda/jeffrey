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
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.QueryBuilder;
import pbouda.jeffrey.provider.api.repository.RecordQuery;

public class DbBasedSubSecondGeneratorImpl implements SubSecondGenerator {

    private final ProfileEventRepository eventsReadRepository;

    public DbBasedSubSecondGeneratorImpl(ProfileEventRepository eventsReadRepository) {
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public JsonNode generate(SubSecondConfig config) {
        RecordQuery recordQuery = QueryBuilder.events(config.profileInfo(), config.eventType().resolveGroupedTypes())
                .from(config.timeRange().start())
                .until(config.timeRange().end())
                .stacktraces()
                .build();

        SubSecondRecordBuilder recordBuilder = new SubSecondRecordBuilder(config);

        eventsReadRepository.streamRecords(recordQuery)
                .forEach(recordBuilder::onRecord);

        SingleResult result = recordBuilder.build();
        return SubSecondCollectorUtils.finisher(result);
    }
}
