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
import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.ConfigBuilder;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.guardian.Guardian;
import pbouda.jeffrey.guardian.GuardianResult;
import pbouda.jeffrey.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.guardian.guard.GuardVisualization;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.common.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;

public class DbBasedGuardianManager implements GuardianManager {

    private final ProfileInfo profileInfo;
    private final Path profileRecordingDir;
    private final Guardian guardian;
    private final CacheRepository cacheRepository;
    private final GraphGenerator flamegraphGenerator;
    private final TimeseriesGenerator timeseriesGenerator;

    public DbBasedGuardianManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            Guardian guardian,
            CacheRepository cacheRepository,
            GraphGenerator flamegraphGenerator,
            TimeseriesGenerator timeseriesGenerator) {

        this.profileInfo = profileInfo;
        this.profileRecordingDir = profileDirs.recordingsDir();
        this.guardian = guardian;
        this.cacheRepository = cacheRepository;
        this.flamegraphGenerator = flamegraphGenerator;
        this.timeseriesGenerator = timeseriesGenerator;
    }

    @Override
    public List<GuardAnalysisResult> guardResults() {
        Config config = new ConfigBuilder<>()
                .withPrimaryId(profileInfo.id())
                .withPrimaryRecordingDir(profileRecordingDir)
                .withParseLocations(false)
                .build();

        return guardian.process(config).stream()
                .map(GuardianResult::analysisItem)
                .toList();
    }

    @Override
    public JsonNode generateFlamegraph(GuardVisualization visualization) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStart(profileInfo.startedAt())
                .withEventType(visualization.eventType())
                .withCollectWeight(visualization.useWeight())
                .build();

        return flamegraphGenerator.generate(config, visualization.markers());
    }

    @Override
    public ArrayNode generateTimeseries(GuardVisualization visualization) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withPrimaryStart(profileInfo.startedAt())
                .withEventType(visualization.eventType())
                .withCollectWeight(visualization.useWeight())
                .build();

        return timeseriesGenerator.generate(config, visualization.markers());
    }
}
