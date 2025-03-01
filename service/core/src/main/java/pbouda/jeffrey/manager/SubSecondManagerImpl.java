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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.generator.subsecond.db.SubSecondConfig;
import pbouda.jeffrey.generator.subsecond.db.api.SubSecondGenerator;

import java.time.Duration;

public class SubSecondManagerImpl implements SubSecondManager {

    private final ProfileInfo profileInfo;
    private final SubSecondGenerator subSecondGenerator;

    public SubSecondManagerImpl(ProfileInfo profileInfo, SubSecondGenerator subSecondGenerator) {
        this.profileInfo = profileInfo;
        this.subSecondGenerator = subSecondGenerator;
    }

    @Override
    public JsonNode generate(Type eventType, boolean collectWeight) {
        SubSecondConfig subSecondConfig = SubSecondConfig.builder()
                .withProfileInfo(profileInfo)
                .withEventType(eventType)
                .withTimeRange(new RelativeTimeRange(Duration.ZERO, Duration.ofMinutes(5)))
                .withCollectWeight(collectWeight)
                .build();

        return subSecondGenerator.generate(subSecondConfig);
    }
}
