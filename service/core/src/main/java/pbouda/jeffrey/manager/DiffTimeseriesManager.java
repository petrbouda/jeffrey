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
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;

import java.nio.file.Path;

public class DiffTimeseriesManager implements TimeseriesManager {

    private final TimeseriesGenerator generator;
    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final Path primaryRecordingDir;
    private final Path secondaryRecordingDir;

    public DiffTimeseriesManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            ProfileDirs primaryProfileDirs,
            ProfileDirs secondaryProfileDirs,
            TimeseriesGenerator generator) {

        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.primaryRecordingDir = primaryProfileDirs.recordingsDir();
        this.secondaryRecordingDir = secondaryProfileDirs.recordingsDir();
        this.generator = generator;
    }

    @Override
    public ArrayNode timeseries(Generate generate) {
        Config timeseriesConfig = Config.differentialBuilder()
                .withPrimaryRecordingDir(primaryRecordingDir)
                .withSecondaryRecordingDir(secondaryRecordingDir)
                .withEventType(generate.eventType())
                .withPrimaryStartEnd(new ProfilingStartEnd(primaryProfileInfo.startedAt(), primaryProfileInfo.endedAt()))
                .withSecondaryStartEnd(new ProfilingStartEnd(secondaryProfileInfo.startedAt(), secondaryProfileInfo.endedAt()))
                .withCollectWeight(generate.useWeight())
                .withThreadInfo(generate.threadInfo())
                // Search is not supported in Differential mode of Timeseries
                .withSearchPattern(null)
                .withExcludeNonJavaSamples(generate.excludeNonJavaSamples())
                .withExcludeIdleSamples(generate.excludeIdleSamples())
                .build();

        return generator.generate(timeseriesConfig, generate.markers());
    }
}