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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.filesystem.ProfileDirs;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;

public class AdhocDiffTimeseriesManager implements TimeseriesManager {

    private final TimeseriesGenerator generator;
    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final Path primaryRecordingDir;
    private final Path secondaryRecordingDir;

    public AdhocDiffTimeseriesManager(
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
    public ArrayNode timeseries(Type eventType, String searchPattern, boolean useWeight) {
        Config timeseriesConfig = Config.differentialBuilder()
                .withPrimaryRecordingDir(primaryRecordingDir)
                .withSecondaryRecordingDir(secondaryRecordingDir)
                .withEventType(eventType)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withCollectWeight(useWeight)
                // Search is not supported in Differential mode of Timeseries
                .withSearchPattern(null)
                .build();

        return generator.generate(timeseriesConfig);
    }
}
