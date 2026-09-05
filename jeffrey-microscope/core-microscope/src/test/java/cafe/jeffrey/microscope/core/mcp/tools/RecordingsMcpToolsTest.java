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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingsMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final String RECORDING_ID = "rec-1";
    private static final String PROFILE_ID = "prof-1";

    @Mock
    RecordingsManager recordingsManager;

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private RecordingsMcpTools tools;
    private PipelineRunRegistry<String> runRegistry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        runRegistry = new PipelineRunRegistry<>(
                ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK);
        tools = new RecordingsMcpTools(recordingsManager, runRegistry);
        // The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private Path recordingFile(String filename) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, "not really a recording, but a real file");
        return file;
    }

    /**
     * Whether the profile behind the recording is finished and usable.
     */
    private void profileIs(boolean enabled) {
        ProfileManager profileManager = mock(ProfileManager.class);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, null, null, "app.jfr", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, enabled, false,
                RECORDING_ID));
        when(recordingsManager.profile(PROFILE_ID)).thenReturn(Optional.of(profileManager));
    }

    private static Recording recording(boolean hasProfile) {
        return new Recording(
                RECORDING_ID, "app.jfr", null, null, RecordingEventSource.JDK,
                START, START, START.plusSeconds(60),
                hasProfile, hasProfile ? PROFILE_ID : null, hasProfile ? "app.jfr" : null, List.of());
    }

    @Nested
    class AnalyzeFile {

        @Test
        void importsTheFileAndReturnsTheProfileItBuilt() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = tools.analyzeFile(file.toString(), null);

            assertTrue(result.contains(PROFILE_ID));
            assertTrue(result.contains(RECORDING_ID));
        }

        /**
         * The profile id is what every other family takes, so it has to come back under a name the
         * model can pick out of the JSON rather than buried in prose.
         */
        @Test
        void namesTheProfileIdInTheResult() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(tools.analyzeFile(file.toString(), null).contains("\"profileId\":\"" + PROFILE_ID + "\""));
        }

        @Test
        void renamesTheProfileWhenAskedTo() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            String result = tools.analyzeFile(file.toString(), "Checkout run");

            verify(recordingsManager).updateProfileName(PROFILE_ID, "Checkout run");
            assertTrue(result.contains("Checkout run"));
        }

        @Test
        void leavesTheProfileNameAloneWhenNoneWasGiven() throws IOException {
            Path file = recordingFile("app.jfr");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            tools.analyzeFile(file.toString(), "   ");

            verify(recordingsManager, never()).updateProfileName(anyString(), anyString());
        }

        /**
         * A relative path is the mistake worth catching by hand: it would resolve against Jeffrey's
         * working directory rather than the caller's repository, so it either finds nothing or — worse —
         * finds a different file with the same name.
         */
        @Test
        void rejectsARelativePathBeforeTouchingTheStore() {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.analyzeFile("target/app.jfr", null));

            assertTrue(e.getMessage().contains("absolute"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAMissingFileAndSaysWhoseFilesystemItLookedOn() {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.analyzeFile(tempDir.resolve("nowhere.jfr").toString(), null));

            assertTrue(e.getMessage().contains("Jeffrey"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAFileTypeJeffreyCannotParse() throws IOException {
            Path file = recordingFile("notes.txt");

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> tools.analyzeFile(file.toString(), null));

            assertTrue(e.getMessage().contains("Unsupported"));
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void rejectsAnAbsentPath() {
            assertThrows(IllegalArgumentException.class, () -> tools.analyzeFile(null, null));
            verifyNoInteractions(recordingsManager);
        }

        /**
         * A heap dump goes down a different path inside the manager, but the tool treats it the same:
         * both end as a profile the read-only families can answer about.
         */
        @Test
        void acceptsAHeapDumpToo() throws IOException {
            Path file = recordingFile("heap.hprof");
            when(recordingsManager.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(tools.analyzeFile(file.toString(), null).contains(PROFILE_ID));
        }
    }

    @Nested
    class AnalyzeRecording {

        @Test
        void analyzesARecordingThatIsAlreadyStored() {
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenReturn(PROFILE_ID);
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));

            assertTrue(tools.analyzeRecording(RECORDING_ID).contains(PROFILE_ID));
            verify(recordingsManager, never()).importRecordingFromPath(any());
        }

        @Test
        void rejectsABlankRecordingId() {
            assertThrows(IllegalArgumentException.class, () -> tools.analyzeRecording("  "));
            verifyNoInteractions(recordingsManager);
        }
    }

    @Nested
    class ListRecordings {

        @Test
        void showsTheProfileOfAnAnalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(true)));

            String result = tools.list();

            assertTrue(result.contains(RECORDING_ID));
            assertTrue(result.contains(PROFILE_ID));
        }

        /**
         * A recording uploaded through the UI but never analysed is exactly what this tool is for —
         * it is invisible to profiles_list until someone builds its profile.
         */
        @Test
        void leavesTheProfileColumnEmptyForAnUnanalysedRecording() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording(false)));

            String row = tools.list().lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertEquals("| " + RECORDING_ID + " | app.jfr | JDK | " + START + " |  |", row);
        }

        @Test
        void explainsAnEmptyStore() {
            when(recordingsManager.listRecordings()).thenReturn(List.of());

            assertTrue(tools.list().contains("empty"));
        }

        @Test
        void keepsAPipeInANameOffTheColumnBoundaries() {
            Recording piped = new Recording(
                    RECORDING_ID, "before|after", null, null, RecordingEventSource.JDK,
                    START, START, START, false, null, null, List.of());
            when(recordingsManager.listRecordings()).thenReturn(List.of(piped));

            String row = tools.list().lines()
                    .filter(line -> line.contains(RECORDING_ID))
                    .findFirst()
                    .orElseThrow();

            assertFalse(row.contains("before|after"));
            assertTrue(row.contains("before/after"));
        }
    }

    /**
     * Parsing a large recording outlasts the call, and what the caller does with that answer decides
     * whether they end up with one profile or two.
     */
    @Nested
    class SlowAnalysis {

        @Test
        void handsBackSomethingToPollRatherThanHangingOn() {
            // A budget short enough that the stubbed work cannot beat it, so the timeout path is the
            // one under test rather than a race.
            RecordingsMcpTools slow = new RecordingsMcpTools(
                    recordingsManager, runRegistry, new BoundedJobs<>(Duration.ofMillis(50)));
            when(recordingsManager.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                Thread.sleep(Duration.ofSeconds(5));
                return PROFILE_ID;
            });

            String result = slow.analyzeRecording(RECORDING_ID);

            assertTrue(result.contains("\"status\":\"running\""));
            assertTrue(result.contains(RECORDING_ID));
            // Explicitly null rather than absent: the field is always there, and its emptiness is what
            // says there is no profile to reach for yet.
            assertTrue(result.contains("\"profileId\":null"), result);
        }

        /**
         * The whole point of a status tool: a second analyze call would parse the file again.
         */
        @Test
        void reportsTheProfileOnceItIsReady() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(true);

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains(PROFILE_ID));
            verify(recordingsManager, never()).analyzeRecording(RECORDING_ID);
        }

        /**
         * The profile row is inserted before the parse starts, so a recording "having" a profile says
         * nothing about whether that profile works yet. Reporting it as finished hands back an id whose
         * events are still being written -- which reads as success, and is the one answer worse than
         * "not yet".
         */
        @Test
        void doesNotReportAProfileThatIsNotEnabledYet() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(false);

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"status\":\"running\""),
                    "a profile that is not enabled is still being built: " + result);
            assertTrue(result.contains("still being written"));
        }

        /**
         * A boolean cannot say whether a parse is a minute in or nearly done; the stages can.
         */
        @Test
        void carriesTheStagesTheRegistryKnowsAbout() {
            runRegistry.runInline(PipelineRunRequest.of(
                    PROFILE_ID, run -> run.runStage(ProfileInitStages.PARSE, () -> {
                    })));
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(false);

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"stages\""));
            assertTrue(result.contains(ProfileInitStages.PARSE), result);
        }

        /**
         * A parse started by a process that has since restarted leaves nothing to report but the state
         * of the profile itself, which is honest rather than empty.
         */
        @Test
        void reportsNoStagesWhenNoRunIsTracked() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(true)));
            profileIs(false);

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"stages\":[]"), result);
            assertTrue(result.contains("\"status\":\"running\""));
        }

        @Test
        void saysNothingIsBuildingOneWhenNothingIs() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording(false)));

            String result = tools.status(RECORDING_ID);

            assertTrue(result.contains("\"status\":\"not_started\""));
        }

        @Test
        void refusesAStatusCallForARecordingThatIsNotThere() {
            when(recordingsManager.findRecording(RECORDING_ID)).thenReturn(Optional.empty());

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.status(RECORDING_ID));

            assertTrue(e.getMessage().contains("No such recording"));
        }
    }

}
