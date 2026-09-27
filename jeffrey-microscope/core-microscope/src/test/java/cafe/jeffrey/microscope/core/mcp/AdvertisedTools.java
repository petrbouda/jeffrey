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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsArtifactsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ToolFixtures;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.mockito.Mockito.mock;

/**
 * Every tool the endpoint can advertise, as {@code tools/list} would list it: every family, the hub
 * and IDE switches on. The collaborators are mocks, because only the specs are read.
 */
final class AdvertisedTools {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private AdvertisedTools() {
    }

    static List<McpToolSpec> all() {
        ExternalMcpProperties properties = McpTestProperties.of(true, true, true, Set.of());
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        RecordingsManager recordingsManager = mock(RecordingsManager.class);
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        McpToolsetAssembler assembler = new McpToolsetAssembler(
                new ProfilesMcpTools(mock(MicroscopeCoreRepositories.class), EVERY_FAMILY),
                RecordingsMcpToolsFixture.of(recordingsManager, new PipelineRunRegistry<>(
                        ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK), operations, CLOCK).build(),
                HubsMcpToolsFixture.of(mock(HubsManager.class), resolver, recordingsManager, CLOCK)
                        .withOperations(operations).build(),
                mock(McpProfileContextCache.class),
                mock(JfrFlamegraphPanelProvider.class),
                mock(StackSampleFlamegraphPanelProvider.class),
                mock(RecordingCommitResolver.class),
                new HeapDumpInitService(CLOCK),
                mock(IdeBridge.class),
                properties,
                AdvertisedFamilies.of(properties),
                HubsArtifactsMcpToolsFixture.of(resolver, recordingsManager, Path.of("artifacts"),
                        Path.of("profiles"), operations, CLOCK, EVERY_FAMILY).build(),
                operations, ToolFixtures.answers(), CLOCK);
        return assembler.toolset().specs();
    }
}
