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

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.common.model.ProfileInfo;

import java.nio.file.Path;

public class PrimaryTimeseriesManager implements TimeseriesManager {

    private final ProfileInfo profileInfo;
    private final TimeseriesGenerator generator;
    private final Path profileRecordingDir;

    public PrimaryTimeseriesManager(
            ProfileInfo profileInfo,
            ProfileDirs profileDirs,
            TimeseriesGenerator generator) {

        this.profileInfo = profileInfo;
        this.profileRecordingDir = profileDirs.recordingsDir();
        this.generator = generator;
    }

    @Override
    public ArrayNode timeseries(Generate generate) {
        Config config = Config.primaryBuilder()
                .withPrimaryRecordingDir(profileRecordingDir)
                .withEventType(generate.eventType())
                .withPrimaryStartEnd(new ProfilingStartEnd(profileInfo.startedAt(), profileInfo.endedAt()))
                .withThreadInfo(generate.threadInfo())
                .withCollectWeight(generate.useWeight())
                .withSearchPattern(generate.searchPattern())
                .withExcludeNonJavaSamples(generate.excludeNonJavaSamples())
                .withExcludeIdleSamples(generate.excludeIdleSamples())
                .build();

        return generator.generate(config, generate.markers());
    }
}
