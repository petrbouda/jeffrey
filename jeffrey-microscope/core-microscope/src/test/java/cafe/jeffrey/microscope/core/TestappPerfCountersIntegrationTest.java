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
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JVM's perf counters the provisioner keeps beside each session's recording: listed with the
 * session's files as an artifact, handed over whole, and — imported — a profile of their own whose
 * counters are the file's, the same JVM the recording watched.
 */
class TestappPerfCountersIntegrationTest extends TestappVolumeMicroscope {

    private static final String ARTIFACT = "ARTIFACT";
    private static final String PERF_COUNTERS_TYPE = "PERF_COUNTERS";
    private static final String YOUNG_COLLECTION = "jdk.YoungGarbageCollection";

    private static final String VM_VERSION = "java.property.java.vm.version";
    private static final String LIVE_THREADS = "java.threads.live";
    private static final String LOADED_CLASSES = "java.cls.loadedClasses";
    private static final String SAFEPOINTS = "sun.rt.safepoints";
    private static final String YOUNG_COLLECTIONS = "sun.gc.collector.0.invocations";

    /** The session lists its counters as an artifact beside its recording, at the file's size. */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void theSessionListsItsCountersAsAnArtifact(TestappRecording recording) {
        JsonNode file = perfCountersOf(recording);

        assertEquals(ARTIFACT, file.path("category").asString());
        assertEquals(PERF_COUNTERS_TYPE, file.path("type").asString());
        assertEquals(recording.perfCounters().length, file.path("sizeBytes").asInt());
    }

    /**
     * Fetched, the counters are handed over by path, byte for byte; imported from there, they are a
     * profile whose counters are the file's — and its young collections are as many as the recording
     * of the same JVM holds.
     *
     * <p>Each run's counters are imported as their own, though every JVM's counters are a file of the
     * same name and the same fixed size — by which an import of any other file is taken for one
     * already made, and which once handed the second run's counters back as the first run's profile.
     */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void theFetchedCountersImportAsTheJvmsOwn(TestappRecording recording) throws IOException {
        JsonNode fetched = microscope.answer("connector_fetchFile", Json.createObject()
                .put("sessionRef", sessionRef(recording.sessionId()))
                .put("fileId", perfCountersOf(recording).path("fileId").asString()));
        Path path = Path.of(fetched.path("path").asString());
        assertArrayEquals(recording.perfCounters(), Files.readAllBytes(path));

        JsonNode imported = microscope.answer("recordings_analyzeFile", Json.createObject().put("path", path.toString()));
        assertEquals("READY", imported.path("status").asString(), imported::toString);
        assertFalse(imported.path("reused").asBoolean(), imported::toString);
        Map<String, String> counters = new HashMap<>();
        microscope.get("/api/internal/profiles/" + imported.path("profileId").asString() + "/perfcounters")
                .forEach(counter -> counters.put(counter.path("key").asString(), counter.path("value").asString()));

        TestappRecording.PerfCounters figures = recording.perfCounterFigures();
        assertEquals(figures.entries(), counters.size());
        assertEquals(TestappRecording.VM_VERSION, counters.get(VM_VERSION));
        assertEquals(String.valueOf(figures.liveThreads()), counters.get(LIVE_THREADS));
        assertEquals(String.valueOf(figures.loadedClasses()), counters.get(LOADED_CLASSES));
        assertEquals(String.valueOf(figures.safepoints()), counters.get(SAFEPOINTS));
        assertEquals(String.valueOf(figures.youngCollections()), counters.get(YOUNG_COLLECTIONS));
        assertEquals(String.valueOf(figures.youngCollections()),
                scalar(recording, "SELECT count(*) FROM events WHERE event_type = '" + YOUNG_COLLECTION + "'"));
    }

    private JsonNode perfCountersOf(TestappRecording recording) {
        JsonNode files = microscope.answer("connector_files", Json.createObject()
                .put("sessionRef", sessionRef(recording.sessionId())));
        JsonNode found = null;
        for (JsonNode file : files.path("files")) {
            if (TestappRecording.PERF_COUNTERS_FILE.equals(file.path("name").asString())) {
                found = file;
            }
        }
        assertNotNull(found, files::toString);
        assertTrue(found.path("sizeBytes").asInt() > 0, found::toString);
        return found;
    }
}
