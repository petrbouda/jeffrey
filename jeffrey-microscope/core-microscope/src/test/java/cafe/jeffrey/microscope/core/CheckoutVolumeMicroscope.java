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
import cafe.jeffrey.test.recording.CheckoutRecording;
import cafe.jeffrey.test.volume.VolumeWriter;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A booted Microscope over a local volume holding the two {@link CheckoutRecording}s — a service that
 * traces its requests — one session each, in one pod: the service restarted with the regression in.
 * Shared by every test class that extends this one; each session is downloaded whole and built into
 * a profile once for all of them.
 */
abstract class CheckoutVolumeMicroscope extends BootedMicroscope {

    static final String WORKSPACE = "dev";
    static final String PROJECT = "checkout";
    static final String POD = "checkout-0";

    /** Minutes after the candidate run ended; its session is the pod's newest. */
    private static final Instant NOW = CheckoutRecording.CANDIDATE.recordingFinishedAt().plus(Duration.ofMinutes(5));

    /** Under the build directory, so {@code mvn clean} takes it with everything else a run leaves. */
    private static final Path RUN = Path.of("target", "checkout-it", UUID.randomUUID().toString()).toAbsolutePath();
    private static final Path HOME = RUN.resolve("home");
    private static final Path VOLUME = RUN.resolve("volume");

    /** Each run's session, by the name the provisioner gave it. */
    static final Map<CheckoutRecording, String> SESSIONS = new EnumMap<>(CheckoutRecording.class);

    /** Each session's profile, by its session id, built on first use and shared by every test after. */
    private static final Map<String, String> PROFILES = new HashMap<>();

    static {
        Path project = VolumeWriter.project(VOLUME.resolve(WORKSPACE), PROJECT);
        Path pod = VolumeWriter.instance(project, POD);
        for (CheckoutRecording recording : CheckoutRecording.values()) {
            Instant openedAt = recording.recordingStartedAt().truncatedTo(ChronoUnit.SECONDS);
            Path session = VolumeWriter.session(pod, openedAt);
            VolumeWriter.recording(session, openedAt, ZoneOffset.UTC, recording.bytes());
            SESSIONS.put(recording, session.getFileName().toString());
        }
    }

    @TestBean(name = "applicationClock")
    Clock applicationClock;

    static Clock applicationClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        declare(registry, HOME, VOLUME);
    }

    /** The profile of a run's session, downloaded whole and built the first time it is asked for. */
    String profile(CheckoutRecording recording) {
        String sessionId = SESSIONS.get(recording);
        synchronized (PROFILES) {
            return PROFILES.computeIfAbsent(sessionId, id -> profileOf(
                    download(sessionRef(id), Json.createObject().put("window", "WHOLE"))));
        }
    }

    /** A tool's structured answer over a run's profile. */
    JsonNode answer(String tool, CheckoutRecording recording, ObjectNode arguments) {
        return microscope.answer(tool, arguments.put("profileId", profile(recording)));
    }

    /** The rows of a query over a run's profile database, each a list of its cells as text. */
    List<List<String>> query(CheckoutRecording recording, String sql) {
        return query(profile(recording), sql);
    }

    /** The rows of a query over a profile's database, each a list of its cells as text. */
    List<List<String>> query(String profileId, String sql) {
        JsonNode result = microscope.answer("jfr_executeQuery",
                Json.createObject().put("profileId", profileId).put("query", sql));
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode row : result.path("rows")) {
            List<String> cells = new ArrayList<>();
            row.forEach(cell -> cells.add(cell.isNull() ? null : cell.asString()));
            rows.add(cells);
        }
        return rows;
    }

    /** The one cell a query over a run's profile database answers. */
    String scalar(CheckoutRecording recording, String sql) {
        List<List<String>> rows = query(recording, sql);
        if (rows.size() != 1 || rows.getFirst().size() != 1) {
            throw new IllegalStateException("Not one cell: " + rows + " from " + sql);
        }
        return rows.getFirst().getFirst();
    }

    /** The trace's id as the tools take it: its 64 bits as 16 hex digits. */
    static String hex(long id) {
        return String.format("%016x", id);
    }

    /** The operation's identity, as every operation tool takes it. */
    static ObjectNode operation() {
        return Json.createObject()
                .put("name", CheckoutRecording.OPERATION)
                .put("kind", CheckoutRecording.OPERATION_KIND)
                .put("eventType", CheckoutRecording.HTTP_SERVER_EXCHANGE);
    }
}
