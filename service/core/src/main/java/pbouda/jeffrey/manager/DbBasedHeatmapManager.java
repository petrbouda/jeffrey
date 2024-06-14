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

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.heatmap.HeatmapConfig;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGenerator;
import pbouda.jeffrey.repository.HeatmapRepository;
import pbouda.jeffrey.repository.model.HeatmapInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Duration;
import java.util.List;

public class DbBasedHeatmapManager implements HeatmapManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final HeatmapRepository heatmapRepository;
    private final HeatmapGenerator heatmapGenerator;

    public DbBasedHeatmapManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            HeatmapRepository heatmapRepository,
            HeatmapGenerator heatmapGenerator) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.heatmapRepository = heatmapRepository;
        this.heatmapGenerator = heatmapGenerator;
    }

    @Override
    public List<HeatmapInfo> all() {
        return heatmapRepository.all(profileInfo.id());
    }

    @Override
    public byte[] contentByName(String heatmapName, Type eventType, boolean collectWeight) {
        return generate(eventType, collectWeight);

//        return heatmapRepository.contentByName(profileInfo.id(), heatmapName)
//                .orElseGet(() -> {
//                    byte[] content = generate(eventType, collectWeight);
//                    heatmapRepository.insert(new HeatmapInfo(profileInfo.id(), heatmapName), content);
//                    return content;
//                });
    }

    private byte[] generate(Type eventType, boolean collectWeight) {
        HeatmapConfig heatmapConfig = HeatmapConfig.builder()
                .withRecording(workingDirs.profileRecording(profileInfo))
                .withEventType(eventType)
                .withProfilingStart(profileInfo.startedAt())
                .withHeatmapStart(Duration.ZERO)
                .withDuration(Duration.ofMinutes(5))
                .withCollectWeight(collectWeight)
                .build();

        return heatmapGenerator.generate(heatmapConfig);
    }

    @Override
    public void delete(String heatmapId) {
        heatmapRepository.delete(profileInfo.id(), heatmapId);
    }

    @Override
    public void cleanup() {
        heatmapRepository.deleteByProfileId(profileInfo.id());
    }
}
