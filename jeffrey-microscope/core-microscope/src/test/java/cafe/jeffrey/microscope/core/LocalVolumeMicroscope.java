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

import cafe.jeffrey.microscope.core.mcp.tools.connector.ConnectorSessionRef;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.test.recording.RecordedSession;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static cafe.jeffrey.test.recording.RecordedSession.CHUNKS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One Microscope booted as it ships — every bean, the real DuckDB, the MCP endpoint and the UI's
 * REST API on a real port — over a local connector laid out as a real volume, shared by every test
 * class that extends this one: they declare the same configuration, so Spring boots it once.
 *
 * <p>The volume holds one workspace and project with three pods:
 * <ul>
 *     <li>{@link #RECORDED_POD}: {@link RecordedSession}'s four real 15-minute async-profiler chunks,
 *     three of them compressed as the hub's compression job leaves them, and beside them the GC log
 *     and crash file the provisioner points the JVM at;</li>
 *     <li>{@link #CUT_SHORT_POD}: a session whose only chunk the profiler was killed while writing;</li>
 *     <li>{@link #DELETED_POD}: a session a test deletes from the UI.</li>
 * </ul>
 * The clock is fixed three minutes after the recorded session's data ends, so it is still its pod's
 * newest session and "the last N minutes" mean the same on every run.
 */
@SpringBootTest(classes = MicroscopeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class LocalVolumeMicroscope {

    static final String WORKSPACE = "uat";
    static final String PROJECT = "jeffrey";
    /** The pod whose session is {@link RecordedSession}. */
    static final String RECORDED_POD = "jeffrey-0";
    /** A pod whose profiler was killed while it wrote its only chunk. */
    static final String CUT_SHORT_POD = "jeffrey-1";
    /** A pod whose session the user deletes from the UI. */
    static final String DELETED_POD = "jeffrey-2";

    /** The GC log the provisioner has the JVM write beside its recording. */
    static final String GC_LOG = "gc.jvm-log";
    static final String GC_LOG_CONTENT = "[2025-12-20T00:12:30.001+0000][info][gc] GC(41) Pause Young (Normal) "
            + "(G1 Evacuation Pause) 612M->188M(1200M) 7.412ms\n";
    /** The crash file the JVM writes where -XX:ErrorFile points. */
    static final String CRASH_FILE = "hs-jvm-err.log";
    static final String CRASH_FILE_CONTENT = "# A fatal error has been detected by the Java Runtime Environment:\n"
            + "#  SIGSEGV (0xb) at pc=0x00007f3c2d8e1a2b, pid=1, tid=27\n";

    static final Instant CUT_SHORT_START = Instant.parse("2025-12-20T00:50:00Z");
    static final Instant DELETED_START = Instant.parse("2025-12-20T00:40:00Z");
    static final String CUT_SHORT_SESSION = VolumeWriter.sessionIdAt(CUT_SHORT_START);
    static final String DELETED_SESSION = VolumeWriter.sessionIdAt(DELETED_START);

    /** Three minutes after the recorded session's data ends: it is still its pod's newest session. */
    static final Instant NOW = RecordedSession.DATA_END.plus(Duration.ofMinutes(3));

    /** Under the build directory, so {@code mvn clean} takes it with everything else a run leaves. */
    static final Path RUN = Path.of("target", "local-connector-it", UUID.randomUUID().toString()).toAbsolutePath();
    static final Path HOME = RUN.resolve("home");
    static final Path VOLUME = RUN.resolve("volume");
    static final Path PROJECT_DIR = VOLUME.resolve(WORKSPACE).resolve(PROJECT);
    static final Path RECORDED_SESSION_DIR = PROJECT_DIR.resolve(RECORDED_POD).resolve(RecordedSession.SESSION_ID);

    static final String CONNECTOR_STATUS = "connector_status";
    static final String CONNECTOR_SESSIONS = "connector_sessions";
    static final String CONNECTOR_DOWNLOAD = "connector_download";
    static final String ANALYZE_RECORDING = "recordings_analyzeRecording";
    static final String PROFILES_SUMMARY = "profiles_summary";

    static final String PROJECT_API = "/api/internal/connector/workspaces/" + WORKSPACE + "/projects/" + PROJECT;

    static {
        RecordedSession.writeAsTheHubLeavesIt(VolumeWriter.instance(PROJECT_DIR, RECORDED_POD));
        VolumeWriter.file(RECORDED_SESSION_DIR, GC_LOG, GC_LOG_CONTENT);
        VolumeWriter.file(RECORDED_SESSION_DIR, CRASH_FILE, CRASH_FILE_CONTENT);

        byte[] whole = CHUNKS.getFirst().bytes();
        Path cutShort = VolumeWriter.session(VolumeWriter.instance(PROJECT_DIR, CUT_SHORT_POD), CUT_SHORT_START);
        VolumeWriter.recording(cutShort, CUT_SHORT_START, ZoneOffset.UTC, Arrays.copyOf(whole, whole.length / 2));

        Path deleted = VolumeWriter.session(VolumeWriter.instance(PROJECT_DIR, DELETED_POD), DELETED_START);
        VolumeWriter.recording(deleted, DELETED_START, ZoneOffset.UTC, CHUNKS.get(2).bytes());
    }

    @TestBean(name = "applicationClock")
    Clock applicationClock;

    static Clock applicationClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jeffrey.microscope.home.dir", HOME::toString);
        registry.add("jeffrey.microscope.connector.local.path", VOLUME::toString);
        registry.add("jeffrey.microscope.update-check.enabled", () -> "false");
        registry.add("jeffrey.microscope.mcp.ide.enabled", () -> "false");
    }

    @LocalServerPort
    int port;

    MicroscopeClient microscope;

    @BeforeEach
    void connect() {
        microscope = new MicroscopeClient(port);
    }

    /** A part of the recorded session, downloaded; it must arrive. */
    JsonNode download(ObjectNode window) {
        JsonNode download = microscope.answer(CONNECTOR_DOWNLOAD, window.put("sessionRef", recordedSessionRef()));
        assertEquals("DOWNLOADED", download.path("status").asString(), download::toString);
        return download;
    }

    /** The profile of what a download brought in, built now or reused. */
    String profileOf(JsonNode download) {
        JsonNode analyzed = microscope.answer(ANALYZE_RECORDING,
                Json.createObject().put("recordingId", download.path("recordingId").asString()));
        assertEquals("READY", analyzed.path("status").asString(), analyzed::toString);
        return analyzed.path("profileId").asString();
    }

    /** How many samples of one event type a profile holds, as its summary reports them. */
    int samples(String profileId, String eventType) {
        JsonNode summary = microscope.answer(PROFILES_SUMMARY, Json.createObject().put("profileId", profileId));
        for (JsonNode type : summary.path("eventTypes")) {
            if (eventType.equals(type.path("name").asString())) {
                return type.path("samples").asInt();
            }
        }
        throw new AssertionError("The profile holds no " + eventType + ": " + summary);
    }

    String recordedSessionRef() {
        return sessionRow(Json.createObject(), RecordedSession.SESSION_ID).path("sessionRef").asString();
    }

    /** A row names its session only inside its opaque ref, so it is found by decoding each one. */
    JsonNode sessionRow(ObjectNode filters, String sessionId) {
        JsonNode page = microscope.answer(CONNECTOR_SESSIONS, filters);
        for (JsonNode row : page.path("sessions")) {
            if (sessionIdOf(row).equals(sessionId)) {
                return row;
            }
        }
        throw new AssertionError("No session " + sessionId + " on the connector: " + page);
    }

    static List<String> sessionIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        for (JsonNode row : page.path("sessions")) {
            ids.add(sessionIdOf(row));
        }
        return ids;
    }

    static String sessionIdOf(JsonNode row) {
        return ConnectorSessionRef.decode(row.path("sessionRef").asString()).sessionId();
    }

    static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
