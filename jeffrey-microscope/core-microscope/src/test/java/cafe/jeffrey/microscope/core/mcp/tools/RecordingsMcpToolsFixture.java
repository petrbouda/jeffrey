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
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;

import java.time.Clock;

/**
 * Builds {@link RecordingsMcpTools} for a test: analysis jobs that wait the standard budget, the
 * standard answers, the default import concurrency and every family advertised, unless the test says
 * otherwise.
 */
public record RecordingsMcpToolsFixture(
        RecordingsManager recordingsManager,
        PipelineRunRegistry<String> runRegistry,
        BoundedJobs<String, String> jobs,
        McpOperationRegistry operations,
        OperationAnswers answers,
        int maxConcurrentImports,
        Clock clock,
        AdvertisedFamilies advertised) {

    public static RecordingsMcpToolsFixture of(
            RecordingsManager recordingsManager, PipelineRunRegistry<String> runRegistry,
            McpOperationRegistry operations, Clock clock) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry,
                ToolFixtures.jobs(BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, clock), operations,
                ToolFixtures.answers(), RecordingsMcpTools.DEFAULT_MAX_CONCURRENT_IMPORTS, clock,
                AdvertisedFamiliesFixture.EVERY_FAMILY);
    }

    public RecordingsMcpToolsFixture withJobs(BoundedJobs<String, String> replacement) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry, replacement, operations, answers,
                maxConcurrentImports, clock, advertised);
    }

    public RecordingsMcpToolsFixture withAnswers(OperationAnswers replacement) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry, jobs, operations, replacement,
                maxConcurrentImports, clock, advertised);
    }

    public RecordingsMcpToolsFixture withAdvertised(AdvertisedFamilies replacement) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry, jobs, operations, answers,
                maxConcurrentImports, clock, replacement);
    }

    public RecordingsMcpTools build() {
        return new RecordingsMcpTools(recordingsManager, runRegistry, jobs, operations, answers,
                maxConcurrentImports, clock, advertised);
    }
}
