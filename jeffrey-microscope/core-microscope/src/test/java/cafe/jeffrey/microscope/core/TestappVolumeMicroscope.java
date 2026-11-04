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
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.test.recording.TestappRecording;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
 * A booted Microscope over a local volume holding the two {@link TestappRecording} sessions exactly as
 * the provisioner laid them down — workspace {@value TestappRecording#WORKSPACE}, a project per mode, the
 * pod it ran in, the session under its own id, its recording and its JVM's perf counters beside it —
 * and each run once more, in a second pod of its project:
 * <ul>
 *     <li>{@link TestappRecording#instance()}: the session as it was delivered, one file;</li>
 *     <li>{@link #SPLIT_POD_SUFFIX}: the same recording as its three chunks in files of their own, named by
 *     when each began — the JDK's two and async-profiler's one side by side in one session, the way two
 *     producers writing separate files leave it — compressed as the hub's compression job leaves them
 *     but the newest of the newest session.</li>
 * </ul>
 * Shared by every test class that extends this one; each session is downloaded whole and built into
 * a profile once for all of them.
 */
abstract class TestappVolumeMicroscope extends BootedMicroscope {

    /** The second pod of each project, holding its run as separate chunk files. */
    static final String SPLIT_POD_SUFFIX = "-split";

    /** The split pod's session was laid down a few seconds before its JVM began recording. */
    private static final Duration SESSION_BEFORE_RECORDING = Duration.ofSeconds(5);
    /** Minutes after both runs ended. */
    private static final Instant NOW = TestappRecording.DOM.recordingFinishedAt().plus(Duration.ofMinutes(5));

    /** Under the build directory, so {@code mvn clean} takes it with everything else a run leaves. */
    private static final Path RUN = Path.of("target", "testapp-it", UUID.randomUUID().toString()).toAbsolutePath();
    private static final Path HOME = RUN.resolve("home");
    private static final Path VOLUME = RUN.resolve("volume");

    private static final String ARCHIVE_SUFFIX = ".lz4";

    /** Each run's split session, by the name it was given. */
    static final Map<TestappRecording, String> SPLIT_SESSIONS = new EnumMap<>(TestappRecording.class);

    /** Each session's profile, by its session id, built on first use and shared by every test after. */
    private static final Map<String, String> PROFILES = new HashMap<>();

    static {
        Path workspace = VOLUME.resolve(TestappRecording.WORKSPACE);
        for (TestappRecording recording : TestappRecording.values()) {
            Path project = VolumeWriter.project(workspace, recording.project());
            Path session = VolumeWriter.session(VolumeWriter.instance(project, recording.instance()), recording.sessionId());
            VolumeWriter.file(session, TestappRecording.RECORDING_FILE, recording.bytes());
            VolumeWriter.file(session, TestappRecording.PERF_COUNTERS_FILE, recording.perfCounters());

            Path splitPod = VolumeWriter.instance(project, recording.instance() + SPLIT_POD_SUFFIX);
            Path split = VolumeWriter.session(splitPod, recording.recordingStartedAt().minus(SESSION_BEFORE_RECORDING));
            layDownChunks(recording, split);
            SPLIT_SESSIONS.put(recording, split.getFileName().toString());
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

    /** The profile of a run's session as delivered, downloaded whole and built the first time it is asked for. */
    String profile(TestappRecording recording) {
        return profileOfSession(recording.sessionId());
    }

    /** The profile of a run's split session, downloaded whole and built the first time it is asked for. */
    String splitProfile(TestappRecording recording) {
        return profileOfSession(SPLIT_SESSIONS.get(recording));
    }

    /** A tool's structured answer over a run's profile. */
    JsonNode answer(String tool, TestappRecording recording, ObjectNode arguments) {
        return microscope.answer(tool, arguments.put("profileId", profile(recording)));
    }

    /** The rows of a query over a run's profile database, each a list of its cells as text. */
    List<List<String>> query(TestappRecording recording, String sql) {
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
    String scalar(TestappRecording recording, String sql) {
        List<List<String>> rows = query(recording, sql);
        if (rows.size() != 1 || rows.getFirst().size() != 1) {
            throw new IllegalStateException("Not one cell: " + rows + " from " + sql);
        }
        return rows.getFirst().getFirst();
    }

    /** The trace's or span's id as the tools take it: its 64 bits as 16 hex digits. */
    static String hex(long id) {
        return String.format("%016x", id);
    }

    private String profileOfSession(String sessionId) {
        synchronized (PROFILES) {
            return PROFILES.computeIfAbsent(sessionId, id -> profileOf(
                    download(sessionRef(id), Json.createObject().put("window", "WHOLE"))));
        }
    }

    /**
     * The run's chunks, cut out of its file and named by when each began; the header of each is
     * checked against the start the fixture pins. Compressed as the hub leaves them: every chunk but
     * the newest of the newest run.
     */
    private static void layDownChunks(TestappRecording recording, Path session) {
        List<byte[]> chunks = JfrFileChunks.split(recording.bytes());
        TestappRecording newest = TestappRecording.values()[TestappRecording.values().length - 1];
        Instant newestStart = recording.chunkStarts().stream().max(Instant::compareTo).orElseThrow();
        for (int i = 0; i < chunks.size(); i++) {
            byte[] bytes = chunks.get(i);
            Instant header = JfrFileChunks.startOf(bytes);
            Instant pinned = recording.chunkStarts().get(i).truncatedTo(ChronoUnit.SECONDS);
            if (!header.truncatedTo(ChronoUnit.SECONDS).equals(pinned)) {
                throw new IllegalStateException(recording + " chunk " + i + " begins at " + header + ", not at " + pinned);
            }
            Path plain = VolumeWriter.recording(session, pinned, ZoneOffset.UTC, bytes);
            boolean keptPlain = recording == newest && recording.chunkStarts().get(i).equals(newestStart);
            if (!keptPlain) {
                Lz4Compressor.compress(plain, session.resolve(plain.getFileName() + ARCHIVE_SUFFIX));
                try {
                    Files.delete(plain);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
