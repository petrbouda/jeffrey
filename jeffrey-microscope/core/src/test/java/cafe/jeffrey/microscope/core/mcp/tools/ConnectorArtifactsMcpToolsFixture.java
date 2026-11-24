/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

import java.nio.file.Path;

/**
 * Builds {@link ConnectorArtifactsMcpTools} for a test. Fetches run synchronously on the call.
 */
public record ConnectorArtifactsMcpToolsFixture(
        ProjectManagerResolver resolver,
        RecordingsManager recordings,
        Path artifactsDir,
        Path profilesDir,
        AdvertisedFamilies advertised) {

    public static ConnectorArtifactsMcpToolsFixture of(
            ProjectManagerResolver resolver, RecordingsManager recordings, Path artifactsDir, Path profilesDir,
            AdvertisedFamilies advertised) {
        return new ConnectorArtifactsMcpToolsFixture(resolver, recordings, artifactsDir, profilesDir, advertised);
    }

    public ConnectorArtifactsMcpTools build() {
        return new ConnectorArtifactsMcpTools(resolver, recordings, artifactsDir, profilesDir, advertised);
    }
}
