/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import cafe.jeffrey.performance.analyst.flamegraph.FlamegraphAiPrompt;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingAiPromptManager;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendation;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.Platform;
import cafe.jeffrey.performance.analyst.persistence.VersionControlSystem;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingRecommendationManagerTest {

    private static final String HUB_ID = "hub-1";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "project-42";
    private static final String PROJECT_NAME = "checkout-service";
    private static final String RECORDING_ID = "rec-1";
    private static final String EVENT_TYPE = "jdk.ExecutionSample";

    private static RecommendationTarget target() {
        return new RecommendationTarget(HUB_ID, WORKSPACE_ID, PROJECT_ID, PROJECT_NAME, RECORDING_ID, EVENT_TYPE);
    }

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-21T18:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempBase;

    private VersionControlSystemManager versionControlSystemManager;
    private RecordingAiPromptManager promptManager;
    private RepositoryCloner repositoryCloner;
    private ChatClient.Builder chatClientBuilder;
    private GeneratedRecommendationRepository recommendationRepository;
    private RecordingRecommendationManager manager;

    @BeforeEach
    void setUp() {
        versionControlSystemManager = mock(VersionControlSystemManager.class);
        promptManager = mock(RecordingAiPromptManager.class);
        repositoryCloner = mock(RepositoryCloner.class);
        chatClientBuilder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        recommendationRepository = mock(GeneratedRecommendationRepository.class);
        manager = new RecordingRecommendationManager(
                versionControlSystemManager, promptManager, repositoryCloner, chatClientBuilder,
                recommendationRepository, FIXED_CLOCK);
    }

    private VersionControlSystem vcs() {
        return new VersionControlSystem(
                "vcs-1", PROJECT_ID, Platform.GITHUB, "https://example.test/acme.git",
                null, Instant.EPOCH, Instant.EPOCH);
    }

    private FlamegraphAiPrompt cpuPrompt() {
        return new FlamegraphAiPrompt(EVENT_TYPE, "CPU", 100, "# flamegraph markdown");
    }

    @Test
    void failsWhenNoRepositoryConfigured() {
        when(versionControlSystemManager.find(PROJECT_ID)).thenReturn(Optional.empty());

        RecommendationProgressSink sink = mock(RecommendationProgressSink.class);
        assertThrows(JeffreyClientException.class,
                () -> manager.generate(target(), sink));
    }

    @Test
    void failsWhenNoPromptForEventType() {
        when(versionControlSystemManager.find(PROJECT_ID)).thenReturn(Optional.of(vcs()));
        when(promptManager.getPrompts(RECORDING_ID)).thenReturn(List.of(
                new FlamegraphAiPrompt("profiler.WallClockSample", "Wall-Clock", 5, "# other")));

        RecommendationProgressSink sink = mock(RecommendationProgressSink.class);
        assertThrows(JeffreyClientException.class,
                () -> manager.generate(target(), sink));
    }

    @Nested
    class HappyPath {

        @Test
        void clonesAnalyzesReturnsAndCleansUp() throws Exception {
            when(versionControlSystemManager.find(PROJECT_ID)).thenReturn(Optional.of(vcs()));
            when(promptManager.getPrompts(RECORDING_ID)).thenReturn(List.of(cpuPrompt()));

            Path cloneRoot = tempBase.resolve("clone");
            Files.createDirectories(cloneRoot);
            ClonedRepository repository = new ClonedRepository(cloneRoot, new TempDirectory(cloneRoot));
            when(repositoryCloner.clone(eq(vcs().url()), eq(null), eq(Platform.GITHUB)))
                    .thenReturn(repository);

            String rawModelOutput = """
                    ===SEVERITY===
                    HIGH
                    ===RECOMMENDATIONS===
                    ## Summary
                    Cache the lookups.
                    ===PATCH===
                    diff --git a/Order.java b/Order.java
                    +cache
                    """;
            when(chatClientBuilder.build().prompt()
                    .system(anyString())
                    .user(anyString())
                    .tools(any())
                    .call()
                    .content()).thenReturn(rawModelOutput);

            RecommendationProgressSink sink = mock(RecommendationProgressSink.class);
            RecommendationResult result = manager.generate(target(), sink);

            // The model output is split into severity + the two artifacts.
            assertEquals(Severity.HIGH, result.severity());
            assertTrue(result.recommendations().contains("## Summary"));
            assertFalse(result.recommendations().contains("diff --git"));
            assertTrue(result.hasPatch());
            assertTrue(result.patch().startsWith("diff --git a/Order.java"));

            // The artifacts + severity + project identity are persisted under (recordingId, eventType).
            ArgumentCaptor<GeneratedRecommendation> stored = ArgumentCaptor.forClass(GeneratedRecommendation.class);
            verify(recommendationRepository).upsert(stored.capture());
            assertEquals(RECORDING_ID, stored.getValue().recordingId());
            assertEquals(EVENT_TYPE, stored.getValue().eventType());
            assertEquals(Severity.HIGH, stored.getValue().severity());
            assertEquals(PROJECT_NAME, stored.getValue().projectName());
            assertTrue(stored.getValue().patch().startsWith("diff --git"));

            // Clone is cloned then analyzed, in that order.
            var order = inOrder(sink);
            order.verify(sink).cloning();
            order.verify(sink).analyzing();

            // The checkout is deleted via the try-with-resources handle.
            assertFalse(Files.exists(cloneRoot), "clone directory should be removed after analysis");
        }
    }
}
