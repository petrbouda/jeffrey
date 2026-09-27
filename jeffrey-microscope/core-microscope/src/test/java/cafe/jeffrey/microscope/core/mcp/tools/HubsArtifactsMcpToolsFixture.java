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
import java.time.Clock;
import java.time.Duration;

/**
 * Builds {@link HubsArtifactsMcpTools} for a test: the standard answers, and the budgets the
 * {@code jeffrey.microscope.mcp.hubs.download-*} properties give production, unless the test says otherwise.
 */
public record HubsArtifactsMcpToolsFixture(
        ProjectManagerResolver resolver,
        RecordingsManager recordings,
        Path artifactsDir,
        Path profilesDir,
        McpOperationRegistry operations,
        OperationAnswers answers,
        Clock clock,
        Duration responseBudget,
        Duration fetchDeadline,
        AdvertisedFamilies advertised) {

    public static final Duration FETCH_DEADLINE = Duration.ofHours(1);

    public static HubsArtifactsMcpToolsFixture of(
            ProjectManagerResolver resolver, RecordingsManager recordings, Path artifactsDir, Path profilesDir,
            McpOperationRegistry operations, Clock clock, AdvertisedFamilies advertised) {
        return new HubsArtifactsMcpToolsFixture(resolver, recordings, artifactsDir, profilesDir, operations,
                ToolFixtures.answers(), clock, BoundedJobs.WAIT_BUDGET, FETCH_DEADLINE, advertised);
    }

    public HubsArtifactsMcpToolsFixture withBudgets(Duration response, Duration deadline) {
        return new HubsArtifactsMcpToolsFixture(resolver, recordings, artifactsDir, profilesDir, operations, answers,
                clock, response, deadline, advertised);
    }

    public HubsArtifactsMcpTools build() {
        return new HubsArtifactsMcpTools(resolver, recordings, artifactsDir, profilesDir, operations, answers, clock,
                responseBudget, fetchDeadline, advertised);
    }
}
