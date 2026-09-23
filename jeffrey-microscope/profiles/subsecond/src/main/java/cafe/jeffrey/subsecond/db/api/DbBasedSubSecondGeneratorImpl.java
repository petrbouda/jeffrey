/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.subsecond.db.api;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.subsecond.db.SingleResult;
import cafe.jeffrey.subsecond.db.SubSecondCollectorUtils;
import cafe.jeffrey.subsecond.db.SubSecondConfig;
import cafe.jeffrey.subsecond.db.SubSecondRecordBuilder;
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
                .withTimeRange(config.timeRange())
                .withWeight(config.collectWeight())
                .withBucketSizeMs(config.bucketSizeMs());

        long startOffsetMillis = config.timeRange() != null ? config.timeRange().start().toMillis() : 0;
        SubSecondRecordBuilder builder = new SubSecondRecordBuilder(startOffsetMillis, config.bucketSizeMs());
        SingleResult result = eventStreamRepository.subSecondStreamer(configurer, builder);
        return SubSecondCollectorUtils.finisher(result);
    }
}
