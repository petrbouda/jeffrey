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

package cafe.jeffrey.profile;

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageProgress;
import cafe.jeffrey.profile.common.pipeline.StageStatus;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.MethodTraceWeightRepository;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the ordering that profile initialization depends on but that no other test can see: the
 * trace tables are derived from the events, so the derivation has to run after the writer has
 * finished and before anything can read a trace.
 * <p>
 * Deliberately a wiring test rather than an end-to-end one. What the repository tests cannot cover
 * is that {@code derive()} is called at all -- a deletion there would leave every trace query
 * returning nothing, with every other test still green.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileInitializerImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private static final String PROFILE_ID = "profile-1";

    @Mock
    ProfileRepositories profileRepositories;

    @Mock
    DatabaseManager databaseManager;

    @Mock
    RecordingEventParserResolver recordingEventParserResolver;

    @Mock
    EventWriter.Factory eventWriterFactory;

    @Mock
    ProfileDataInitializer profileDataInitializer;

    @Mock
    TraceRepository traceRepository;

    @Mock
    TraceAttributeRepository traceAttributeRepository;

    @Mock
    MethodTraceWeightRepository methodTraceWeightRepository;

    @Mock
    EventWriter eventWriter;

    @Mock
    RecordingEventParser recordingEventParser;

    private final PipelineRunRegistry<String> runRegistry =
            new PipelineRunRegistry<>(ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK);

    private ProfileInitializerImpl initializer(ProfileInfo profileInfo) {
        DataSource dataSource = mock(DataSource.class);
        DatabaseLease lease = mock(DatabaseLease.class);
        when(lease.dataSource()).thenReturn(dataSource);
        when(databaseManager.acquire(profileInfo.id())).thenReturn(lease);

        when(eventWriterFactory.create(any(), any())).thenReturn(eventWriter);
        when(recordingEventParserResolver.resolve(any())).thenReturn(recordingEventParser);
        when(profileRepositories.newTraceRepository(dataSource)).thenReturn(traceRepository);
        when(profileRepositories.newTraceAttributeRepository(dataSource))
                .thenReturn(traceAttributeRepository);
        when(profileRepositories.newMethodTraceWeightRepository(dataSource))
                .thenReturn(methodTraceWeightRepository);

        // The re-cluster and checkpoint steps at the tail run through the infrastructure client.
        DatabaseClientProvider clientProvider = mock(DatabaseClientProvider.class);
        when(clientProvider.provide(any())).thenReturn(mock(DatabaseClient.class));
        when(profileRepositories.databaseClientProvider(dataSource)).thenReturn(clientProvider);

        return new ProfileInitializerImpl(
                profileRepositories,
                databaseManager,
                recordingEventParserResolver,
                eventWriterFactory,
                // Deep stubs: initialization reaches through the manager to the additional-files
                // manager, and every one of those hops would otherwise be a null.
                _ -> mock(ProfileManager.class, RETURNS_DEEP_STUBS),
                profileDataInitializer,
                runRegistry,
                CLOCK);
    }

    @Test
    @DisplayName("derives the trace tables once the events are written")
    void derivesTracesAfterParsing() {
        ProfileInfo profileInfo = mock(ProfileInfo.class);
        when(profileInfo.id()).thenReturn(PROFILE_ID);
        when(traceRepository.hasSpanEventTypes()).thenReturn(true);

        initializer(profileInfo).initialize(profileInfo, null, Path.of("recording.jfr"));

        // Before the writer completes there is nothing to derive from; after the data initializer
        // the pre-computed views would have been built against tables that were still empty.
        // The attribute index reads trace_spans, so it derives strictly after the spans do.
        InOrder inOrder =
                inOrder(eventWriter, traceRepository, traceAttributeRepository, profileDataInitializer);
        inOrder.verify(eventWriter).onComplete();
        inOrder.verify(traceRepository).derive();
        inOrder.verify(traceAttributeRepository).derive();
        inOrder.verify(profileDataInitializer).initialize(any());
    }

    @Test
    @DisplayName("skips the derivation entirely when the recording carries no spans")
    void skipsDerivationWithoutSpanEventTypes() {
        ProfileInfo profileInfo = mock(ProfileInfo.class);
        when(profileInfo.id()).thenReturn(PROFILE_ID);
        when(traceRepository.hasSpanEventTypes()).thenReturn(false);

        initializer(profileInfo).initialize(profileInfo, null, Path.of("recording.jfr"));

        // Both derivations read every event of the blocking types before they can conclude there is
        // nothing to attach them to, so an ordinary profiling recording pays a full scan for an
        // empty result. Asking first costs one row-less probe of event_types.
        verify(traceRepository, never()).derive();
        verify(traceAttributeRepository, never()).derive();

        // The rest of the initialization is unaffected -- skipping traces is not skipping the profile.
        verify(profileDataInitializer).initialize(any());
    }

    @Nested
    @DisplayName("Progress")
    class Progress {

        private StageProgress stage(String id) {
            return runRegistry.progress(PROFILE_ID).stages().stream()
                    .filter(stage -> stage.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No such stage: " + id));
        }

        @Test
        @DisplayName("records every stage of a successful initialization")
        void recordsEveryStage() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);
            when(traceRepository.hasSpanEventTypes()).thenReturn(true);

            initializer(profileInfo).initialize(profileInfo, "recording-1", Path.of("recording.jfr"));

            PipelineProgress progress = runRegistry.progress(PROFILE_ID);
            assertEquals(PipelineState.COMPLETED, progress.state());
            assertEquals(StageStatus.COMPLETED, stage(ProfileInitStages.PARSE).status());
            assertEquals(StageStatus.COMPLETED, stage(ProfileInitStages.TRACES).status());
            assertEquals(StageStatus.COMPLETED, stage(ProfileInitStages.WARMUP).status());
        }

        /**
         * The difference worth surfacing: a recording with no spans did not have a fast trace
         * derivation, it had none at all, and the progress should say so rather than leaving the
         * reader to infer it from a suspiciously short duration.
         */
        @Test
        @DisplayName("reports a recording without spans as a skipped derivation")
        void reportsSkippedTraceDerivation() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);
            when(traceRepository.hasSpanEventTypes()).thenReturn(false);

            initializer(profileInfo).initialize(profileInfo, "recording-1", Path.of("recording.jfr"));

            assertEquals(StageStatus.SKIPPED, stage(ProfileInitStages.TRACES).status());
            assertEquals(PipelineState.COMPLETED, runRegistry.progress(PROFILE_ID).state());
        }

        /**
         * jdk.MethodTrace only fires for methods a JFR filter named, so nearly every recording has
         * none. Same reasoning as the trace derivation above: skipped says "there was nothing to
         * do", a fast success says "there was, and it was quick".
         */
        @Test
        @DisplayName("reports a recording that traced no methods as a skipped weight derivation")
        void reportsSkippedMethodTraceWeights() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);
            when(methodTraceWeightRepository.hasMethodTraces()).thenReturn(false);

            initializer(profileInfo).initialize(profileInfo, "recording-1", Path.of("recording.jfr"));

            assertEquals(StageStatus.SKIPPED, stage(ProfileInitStages.METHOD_TRACE_WEIGHTS).status());
            verify(methodTraceWeightRepository, never()).deriveSelfWeights();
        }

        @Test
        @DisplayName("derives self weights when the recording traced methods")
        void derivesMethodTraceWeights() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);
            when(methodTraceWeightRepository.hasMethodTraces()).thenReturn(true);

            initializer(profileInfo).initialize(profileInfo, "recording-1", Path.of("recording.jfr"));

            assertEquals(StageStatus.COMPLETED, stage(ProfileInitStages.METHOD_TRACE_WEIGHTS).status());
            verify(methodTraceWeightRepository).deriveSelfWeights();
        }

        @Test
        @DisplayName("a profile with no project skips the context stage rather than pretending to run it")
        void reportsSkippedProfileInfo() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);

            initializer(profileInfo).initialize(profileInfo, null, Path.of("recording.jfr"));

            assertEquals(StageStatus.SKIPPED, stage(ProfileInitStages.PROFILE_INFO).status());
            assertEquals(StageStatus.SKIPPED, stage(ProfileInitStages.ADDITIONAL_FILES).status());
        }

        /**
         * The gap this closes: a failed initialization used to leave the profile row disabled with
         * nothing anywhere recording why, because the exception went into a future nobody held.
         */
        @Test
        @DisplayName("a failing stage fails the run, names itself, and still reaches the caller")
        void failureIsRecordedAndPropagated() {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.id()).thenReturn(PROFILE_ID);
            doThrow(new IllegalStateException("recording is corrupt"))
                    .when(recordingEventParser).start(any(), any());

            ProfileInitializerImpl initializer = initializer(profileInfo);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> initializer.initialize(profileInfo, "recording-1", Path.of("recording.jfr")));
            assertEquals("recording is corrupt", thrown.getMessage());

            PipelineProgress progress = runRegistry.progress(PROFILE_ID);
            assertEquals(PipelineState.FAILED, progress.state());
            assertEquals(StageStatus.FAILED, stage(ProfileInitStages.PARSE).status());
        }
    }
}
