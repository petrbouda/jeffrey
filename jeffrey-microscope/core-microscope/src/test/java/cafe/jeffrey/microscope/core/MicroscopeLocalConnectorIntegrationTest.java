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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.RecordedSession;
import cafe.jeffrey.test.recording.RecordedSession.Chunk;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.test.recording.RecordedSession.CHUNKS;
import static cafe.jeffrey.test.recording.RecordedSession.EXECUTION_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connector over a local directory, end to end: what the agent and the UI are told about the
 * volume, and what each window of the recorded session brings in. Every figure is what the chunks
 * hold, carried from the volume through the download, the parser and DuckDB to the tool's answer:
 * the last half hour of the session holds exactly the CPU samples of its last two chunks.
 */
class MicroscopeLocalConnectorIntegrationTest extends LocalVolumeMicroscope {

    private static final Path VOLUME_AWAY = RUN.resolve("volume-away");
    /** The session's chunks and the two artifacts beside them. */
    private static final int RECORDED_SESSION_FILES = CHUNKS.size() + 2;

    @Nested
    class Status {

        @Test
        void reachesTheDirectoryAsALocalConnector() {
            JsonNode status = microscope.answer(CONNECTOR_STATUS, Json.createObject());

            assertEquals("REACHABLE", status.path("state").asString());
            assertEquals("LOCAL", status.path("kind").asString());
            assertEquals("local:" + VOLUME, status.path("address").asString());
            assertFalse(status.path("readOnly").asBoolean());
        }

        /** Read live: no restart between the directory going away and coming back. */
        @Test
        void aDirectoryThatGoesAwayIsUnreachableUntilItIsBack() throws IOException {
            Files.move(VOLUME, VOLUME_AWAY);
            try {
                assertEquals("UNREACHABLE",
                        microscope.answer(CONNECTOR_STATUS, Json.createObject()).path("state").asString());
            } finally {
                Files.move(VOLUME_AWAY, VOLUME);
            }

            assertEquals("REACHABLE",
                    microscope.answer(CONNECTOR_STATUS, Json.createObject()).path("state").asString());
        }
    }

    @Nested
    class Sessions {

        /** Its start comes from its name; its end from its newest chunk's name and the chunks' spacing. */
        @Test
        void findsTheRecordedSessionWithItsSpan() {
            JsonNode session = sessionRow(Json.createObject(), RecordedSession.SESSION_ID);

            assertEquals(WORKSPACE, session.path("workspace").asString());
            assertEquals(PROJECT, session.path("project").asString());
            assertEquals(RecordedSession.SESSION_START.toEpochMilli(), session.path("startedAtEpochMs").asLong());
            assertEquals(Duration.between(RecordedSession.SESSION_START, RecordedSession.DATA_END).toMillis(),
                    session.path("durationMs").asLong());
            assertEquals(RECORDED_SESSION_FILES, session.path("files").asInt());
            assertTrue(session.path("newest").asBoolean());
        }

        /** An overlap with the last ten minutes: only the recorded session's data reaches into them. */
        @Test
        void aRecentWindowKeepsOnlyTheSessionWhoseDataReachesIntoIt() {
            JsonNode page = microscope.answer(CONNECTOR_SESSIONS, Json.createObject().put("withinLastMinutes", 10));

            assertEquals(List.of(RecordedSession.SESSION_ID), sessionIds(page));
        }
    }

    /**
     * Each window is downloaded and built into a profile, and the profile's CPU samples are counted:
     * they are what the chunks the window took hold, archives and the plain newest chunk alike.
     */
    @Nested
    class Windows {

        @Test
        void theLastHalfHourIsTheLastTwoChunks() {
            JsonNode download = download(Json.createObject().put("window", "LAST_MINUTES").put("minutes", 30));

            assertEquals(2, download.path("recordingFiles").asInt());
            assertEquals(CHUNKS.get(2).openedAt().toEpochMilli(), download.path("startEpochMs").asLong());
            assertEquals(RecordedSession.DATA_END.toEpochMilli(), download.path("endEpochMs").asLong());
            assertEquals(RecordedSession.executionSamples(CHUNKS.subList(2, 4)),
                    samples(profileOf(download), EXECUTION_SAMPLE));
        }

        /** The whole session brings the files beside its recording too. */
        @Test
        void theWholeSessionIsEveryChunkAndItsArtifacts() {
            JsonNode download = download(Json.createObject().put("window", "WHOLE"));

            assertEquals(CHUNKS.size(), download.path("recordingFiles").asInt());
            assertEquals(2, download.path("artifactFiles").asInt());
            assertEquals(RecordedSession.executionSamples(CHUNKS), samples(profileOf(download), EXECUTION_SAMPLE));
        }

        /** The peak compares compressed sizes only: the largest archive, never the uncompressed newest chunk. */
        @Test
        void thePeakIsTheLargestArchive() throws IOException {
            Chunk largest = largestArchive();

            JsonNode download = download(Json.createObject().put("window", "PEAK"));

            assertEquals(List.of(largest.fileId()), strings(download.path("fileIds")));
            assertEquals(largest.executionSamples(), samples(profileOf(download), EXECUTION_SAMPLE));
        }

        @Test
        void theLatestIsTheNewestChunk() {
            JsonNode download = download(Json.createObject().put("window", "LATEST"));

            assertEquals(List.of(CHUNKS.getLast().fileId()), strings(download.path("fileIds")));
            assertEquals(CHUNKS.getLast().executionSamples(), samples(profileOf(download), EXECUTION_SAMPLE));
        }

        /** Retention took the session's first chunks: its oldest kept one starts 45 minutes after it. */
        @Test
        void theStartupIsNoLongerOnTheVolume() {
            JsonNode answer = microscope.answer(CONNECTOR_DOWNLOAD,
                    Json.createObject().put("sessionRef", recordedSessionRef()).put("window", "STARTUP"));

            assertEquals("STARTUP_NOT_RETAINED", answer.path("status").asString());
            assertTrue(answer.path("recordingId").isNull());
        }

        private Chunk largestArchive() throws IOException {
            Chunk largest = null;
            long largestSize = -1;
            for (Chunk chunk : CHUNKS.subList(0, CHUNKS.size() - 1)) {
                long size = Files.size(RECORDED_SESSION_DIR.resolve(chunk.archivedName()));
                if (size > largestSize) {
                    largest = chunk;
                    largestSize = size;
                }
            }
            return largest;
        }
    }

    @Nested
    class Refusals {

        /** One recording reports one span, so a pick that skips a chunk would leave a silent hole. */
        @Test
        void aPickThatSkipsAChunkIsRefusedNamingIt() {
            ObjectNode arguments = Json.createObject().put("sessionRef", recordedSessionRef());
            arguments.putArray("fileIds").add(CHUNKS.get(0).fileId()).add(CHUNKS.get(2).fileId());

            String refusal = microscope.refusal(CONNECTOR_DOWNLOAD, arguments);

            assertTrue(refusal.contains(CHUNKS.get(1).archivedName()), refusal);
        }

        /**
         * The profiler was killed mid-write: the copy is checked on arrival, the transfer fails as an
         * outcome the agent may retry, and the volume is left as it is.
         */
        @Test
        void aChunkCutShortFailsTheTransferAndIsLeftOnTheVolume() {
            String ref = sessionRow(Json.createObject(), CUT_SHORT_SESSION).path("sessionRef").asString();

            JsonNode answer = microscope.answer(CONNECTOR_DOWNLOAD,
                    Json.createObject().put("sessionRef", ref).put("window", "WHOLE"));

            assertEquals("FAILED", answer.path("status").asString());
            // The sentence the agent reads, with no Java class name in front of it
            String chunk = VolumeWriter.recordingName(CUT_SHORT_START, ZoneOffset.UTC);
            assertTrue(answer.path("reason").asString().startsWith("Recording " + chunk + " arrived incomplete"),
                    answer::toString);
            assertTrue(answer.path("operation").path("retryable").asBoolean());
            assertTrue(answer.path("recordingId").isNull());
            assertTrue(Files.exists(PROJECT_DIR.resolve(CUT_SHORT_POD).resolve(CUT_SHORT_SESSION).resolve(chunk)));
        }
    }

    /** The UI's pages read the same volume, through the REST API. */
    @Nested
    class UiPages {

        @Test
        void theConnectorPageNamesTheDirectory() {
            JsonNode connector = microscope.get("/api/internal/connector").path("connector");

            assertEquals("LOCAL", connector.path("kind").asString());
            assertEquals(VOLUME.toString(), connector.path("location").asString());
            assertFalse(connector.path("readOnly").asBoolean());
        }

        @Test
        void theInstancePageListsTheSessionByItsName() {
            JsonNode page = microscope.get(PROJECT_API + "/instances/" + RECORDED_POD);

            assertEquals(1, page.path("sessions").size());
            JsonNode session = page.path("sessions").get(0);
            assertEquals(RecordedSession.SESSION_ID, session.path("id").asString());
            assertEquals(RecordedSession.SESSION_START.toEpochMilli(), session.path("createdAt").asLong());
            assertTrue(session.path("newest").asBoolean());
        }

        /** Three archives and the newest chunk as the profiler left it, which ends the session's data. */
        @Test
        void anOpenedSessionCarriesItsFilesAndWhereItsDataEnds() {
            JsonNode session = microscope.get(PROJECT_API + "/repository/sessions/" + RecordedSession.SESSION_ID);

            assertEquals(RecordedSession.DATA_END.toEpochMilli(), session.path("lastDataAt").asLong());
            assertEquals(RECORDED_SESSION_FILES, session.path("files").size());
            List<String> recordings = new ArrayList<>();
            List<String> newest = new ArrayList<>();
            for (JsonNode file : session.path("files")) {
                if (file.path("isRecording").asBoolean()) {
                    recordings.add(file.path("fileType").asString());
                }
                if (file.path("newestRecording").asBoolean()) {
                    newest.add(file.path("name").asString());
                }
            }
            assertEquals(List.of("JFR", "JFR_LZ4", "JFR_LZ4", "JFR_LZ4"), recordings);
            assertEquals(List.of(CHUNKS.getLast().volumeName()), newest);
            assertFalse(session.path("failed").asBoolean());
        }

        /** Its pod held nothing else, so the pod's directory goes with it. */
        @Test
        void deletingASessionRemovesItAndItsEmptyPod() {
            HttpResponse<String> response = microscope.delete(PROJECT_API + "/repository/sessions/" + DELETED_SESSION);

            assertEquals(200, response.statusCode(), response.body());
            assertFalse(Files.exists(PROJECT_DIR.resolve(DELETED_POD)));
            assertTrue(Files.exists(RECORDED_SESSION_DIR));
        }
    }
}
