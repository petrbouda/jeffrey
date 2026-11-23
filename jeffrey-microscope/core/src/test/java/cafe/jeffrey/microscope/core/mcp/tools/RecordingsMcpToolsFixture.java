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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.core.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.microscope.core.pipeline.PipelineRunRegistry;

import static org.mockito.Mockito.mock;

/**
 * Builds {@link RecordingsMcpTools} for a test: the standard commit resolver and every family
 * advertised, unless the test says otherwise. Analysis runs synchronously on the call.
 */
public record RecordingsMcpToolsFixture(
        RecordingsManager recordingsManager,
        PipelineRunRegistry<String> runRegistry,
        AdvertisedFamilies advertised,
        RecordingCommitResolver revisions) {

    public static RecordingsMcpToolsFixture of(
            RecordingsManager recordingsManager, PipelineRunRegistry<String> runRegistry) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry,
                AdvertisedFamiliesFixture.EVERY_FAMILY,
                new RecordingCommitResolver(mock(RecordingTagsRepository.class)));
    }

    public RecordingsMcpToolsFixture withAdvertised(AdvertisedFamilies replacement) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry, replacement, revisions);
    }

    public RecordingsMcpToolsFixture withRevisions(RecordingCommitResolver replacement) {
        return new RecordingsMcpToolsFixture(recordingsManager, runRegistry, advertised, replacement);
    }

    public RecordingsMcpTools build() {
        return new RecordingsMcpTools(recordingsManager, runRegistry, advertised, revisions);
    }
}
