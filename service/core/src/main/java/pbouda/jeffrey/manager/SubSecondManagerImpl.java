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
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.generator.subsecond.SubSecondConfig;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGenerator;
import pbouda.jeffrey.repository.SubSecondRepository;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.repository.model.SubSecondInfo;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class SubSecondManagerImpl implements SubSecondManager {

    private final ProfileInfo profileInfo;
    private final Path profileRecordingDir;
    private final SubSecondRepository subSecondRepository;
    private final SubSecondGenerator subSecondGenerator;

    public SubSecondManagerImpl(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            SubSecondRepository subSecondRepository,
            SubSecondGenerator subSecondGenerator) {

        this.profileInfo = profileInfo;
        this.profileRecordingDir = profileDirs.recordingsDir();
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
                .withRecordingDir(profileRecordingDir)
                .withEventType(eventType)
                .withProfilingStart(profileInfo.startedAt())
                .withGeneratingStart(Duration.ZERO)
                .withDuration(Duration.ofMinutes(5))
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
