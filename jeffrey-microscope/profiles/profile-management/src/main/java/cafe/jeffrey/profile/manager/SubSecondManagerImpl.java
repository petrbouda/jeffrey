/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.subsecond.db.SubSecondConfig;
import cafe.jeffrey.subsecond.db.api.SubSecondGenerator;

import java.time.Duration;

public class SubSecondManagerImpl implements SubSecondManager {

    private final ProfileInfo profileInfo;
    private final SubSecondGenerator subSecondGenerator;

    public SubSecondManagerImpl(ProfileInfo profileInfo, SubSecondGenerator subSecondGenerator) {
        this.profileInfo = profileInfo;
        this.subSecondGenerator = subSecondGenerator;
    }

    @Override
    public JsonNode generate(Type eventType, boolean collectWeight, RelativeTimeRange timeRange, int bucketSizeMs) {
        RelativeTimeRange effectiveRange = timeRange != null
                ? timeRange
                : new RelativeTimeRange(Duration.ZERO, Duration.ofMinutes(5));

        SubSecondConfig subSecondConfig = SubSecondConfig.builder()
                .withProfileInfo(profileInfo)
                .withEventType(eventType)
                .withTimeRange(effectiveRange)
                .withCollectWeight(collectWeight)
                .withBucketSizeMs(bucketSizeMs)
                .build();

        return subSecondGenerator.generate(subSecondConfig);
    }
}
