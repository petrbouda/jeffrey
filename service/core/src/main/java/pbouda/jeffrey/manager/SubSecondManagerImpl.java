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
import pbouda.jeffrey.repository.SubSecondRepository;
import pbouda.jeffrey.repository.model.SubSecondInfo;

import java.time.Duration;
import java.util.List;

public class SubSecondManagerImpl implements SubSecondManager {

    private final ProfileInfo profileInfo;
    private final SubSecondRepository subSecondRepository;
    private final SubSecondGenerator subSecondGenerator;

    public SubSecondManagerImpl(
            ProfileInfo profileInfo,
            SubSecondRepository subSecondRepository,
            SubSecondGenerator subSecondGenerator) {

        this.profileInfo = profileInfo;
        this.subSecondRepository = subSecondRepository;
        this.subSecondGenerator = subSecondGenerator;
    }

    @Override
    public List<SubSecondInfo> all() {
        return subSecondRepository.all(profileInfo.id());
    }

    @Override
    public JsonNode generate(Type eventType, boolean collectWeight) {
        SubSecondConfig subSecondConfig = SubSecondConfig.builder()
                .withEventType(eventType)
                .withTimeRange(new RelativeTimeRange(Duration.ZERO, Duration.ofMinutes(5)))
                .withCollectWeight(collectWeight)
                .build();

        return subSecondGenerator.generate(subSecondConfig);
    }

    @Override
    public void delete(String subSecondId) {
        subSecondRepository.delete(profileInfo.id(), subSecondId);
    }

    @Override
    public void cleanup() {
        subSecondRepository.deleteByProfileId(profileInfo.id());
    }
}
