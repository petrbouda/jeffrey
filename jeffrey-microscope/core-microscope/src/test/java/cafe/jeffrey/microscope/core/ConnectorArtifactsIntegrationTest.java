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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files beside the recorded session's recording — the GC log and the crash file the provisioner
 * points the JVM at — listed for the agent and handed over as a path on this machine, never read
 * by the server. The agent greps the file itself.
 */
class ConnectorArtifactsIntegrationTest extends LocalVolumeMicroscope {

    private static final String FETCH_FILE = "connector_fetchFile";
    private static final String ARTIFACT = "ARTIFACT";

    /** Both artifacts, by name, as the session's listing describes them, fetchable one at a time. */
    @Test
    void theSessionListsItsArtifactsBesideItsChunks() {
        JsonNode files = microscope.answer("connector_files", Json.createObject().put("sessionRef", recordedSessionRef()));

        Map<String, String> artifacts = new TreeMap<>();
        for (JsonNode file : files.path("files")) {
            if (ARTIFACT.equals(file.path("category").asString())) {
                assertEquals("FETCH", file.path("fetch").asString(), file::toString);
                artifacts.put(file.path("name").asString(), file.path("type").asString());
            }
        }
        assertEquals(Map.of(GC_LOG, "JVM_LOG", CRASH_FILE, "HS_JVM_ERROR_LOG"), artifacts);
        assertEquals(6, files.path("total").asInt());
    }

    /**
     * Fetched beside the session's profile once the session is analysed: the path is absolute,
     * inside Microscope's home, holds the file's bytes, and a second fetch answers from disk.
     */
    @Test
    void anArtifactIsHandedOverAsAPathHoldingItsBytes() throws IOException {
        profileOf(download(Json.createObject().put("window", "WHOLE")));
        String profileId = sessionRow(Json.createObject(), RecordedSession.SESSION_ID)
                .path("profileId").asString();

        JsonNode fetched = microscope.answer(FETCH_FILE,
                Json.createObject().put("sessionRef", recordedSessionRef()).put("fileId", GC_LOG));
        JsonNode again = microscope.answer(FETCH_FILE,
                Json.createObject().put("sessionRef", recordedSessionRef()).put("fileId", GC_LOG));

        assertEquals("FETCHED", fetched.path("status").asString(), fetched::toString);
        Path path = Path.of(fetched.path("path").asString());
        assertTrue(path.isAbsolute(), path::toString);
        assertEquals(HOME.resolve("profiles").resolve(profileId).resolve("artifacts").resolve(GC_LOG), path);
        assertEquals(GC_LOG_CONTENT, Files.readString(path));
        assertTrue(again.path("alreadyHere").asBoolean(), again::toString);
        assertEquals(path.toString(), again.path("path").asString());
    }

    @Test
    void theCrashFileArrivesWhole() throws IOException {
        JsonNode fetched = microscope.answer(FETCH_FILE,
                Json.createObject().put("sessionRef", recordedSessionRef()).put("fileId", CRASH_FILE));

        Path path = Path.of(fetched.path("path").asString());
        assertTrue(path.startsWith(HOME), path::toString);
        assertEquals(CRASH_FILE_CONTENT, Files.readString(path));
    }

    /** The id comes off the wire: a path in it names no file of the session and writes nothing. */
    @Test
    void anIdThatClimbsOutOfTheSessionIsRefusedAndWritesNothing() {
        String refusal = microscope.refusal(FETCH_FILE, Json.createObject()
                .put("sessionRef", recordedSessionRef()).put("fileId", "../../../" + GC_LOG));

        assertTrue(refusal.contains("has no file with id ../../../" + GC_LOG), refusal);
        assertTrue(refusal.contains("connector_files"), refusal);
        assertFalse(Files.exists(HOME.getParent().resolve(GC_LOG)));
        assertFalse(Files.exists(RUN.getParent().resolve(GC_LOG)));
    }
}
