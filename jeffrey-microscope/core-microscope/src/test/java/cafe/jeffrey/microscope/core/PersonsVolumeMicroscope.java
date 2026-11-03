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
import cafe.jeffrey.test.volume.VolumeWriter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A booted Microscope over a local volume holding the two {@link PersonsRecording}s, each run twice
 * over, in two pods of one project — the service restarted with the other serialisation:
 * <ul>
 *     <li>{@link #POD}: each run as the one file it was recorded into, LZ4-compressed;</li>
 *     <li>{@link #CHUNKED_POD}: each run as the chunks a profiler writing a file per chunk leaves —
 *     the file split into async-profiler's three chunks, every one compressed as the hub's
 *     compression job leaves them but the newest chunk of the pod's newest session.</li>
 * </ul>
 * Shared by every test class that extends this one; each session is downloaded whole and built into
 * a profile once for all of them.
 */
abstract class PersonsVolumeMicroscope extends BootedMicroscope {

    static final String WORKSPACE = "dev";
    static final String PROJECT = "persons";
    static final String POD = "persons-0";
    static final String CHUNKED_POD = "persons-1";

    /** The chunked pod's provisioner laid each session down a few seconds before its JVM began recording. */
    private static final Duration SESSION_BEFORE_RECORDING = Duration.ofSeconds(5);
    /** Minutes after the later run ended; its session is the pod's newest. */
    private static final Instant NOW = PersonsRecording.DOM.startedAt().plus(Duration.ofMinutes(37));

    /** Under the build directory, so {@code mvn clean} takes it with everything else a run leaves. */
    private static final Path RUN = Path.of("target", "persons-it", UUID.randomUUID().toString()).toAbsolutePath();
    private static final Path HOME = RUN.resolve("home");
    private static final Path VOLUME = RUN.resolve("volume");

    private static final String ARCHIVE_SUFFIX = ".lz4";

    /** Each run's session of one file, by the name the provisioner gave it. */
    static final Map<PersonsRecording, String> SESSIONS = new EnumMap<>(PersonsRecording.class);

    /** Each run's session of chunks. */
    static final Map<PersonsRecording, String> CHUNKED_SESSIONS = new EnumMap<>(PersonsRecording.class);

    /** Each run's chunked session's directory, where its chunks lie. */
    static final Map<PersonsRecording, Path> CHUNKED_SESSION_DIRS = new EnumMap<>(PersonsRecording.class);

    /** Each session's profile, by its session id, built on first use and shared by every test after. */
    private static final Map<String, String> PROFILES = new HashMap<>();

    static {
        Path project = VolumeWriter.project(VOLUME.resolve(WORKSPACE), PROJECT);
        Path pod = VolumeWriter.instance(project, POD);
        Path chunkedPod = VolumeWriter.instance(project, CHUNKED_POD);
        PersonsRecording newest = PersonsRecording.values()[PersonsRecording.values().length - 1];
        for (PersonsRecording recording : PersonsRecording.values()) {
            Path session = VolumeWriter.session(pod, recording.startedAt());
            Path archive = VolumeWriter.file(session, archiveName(recording.startedAt()), recording.bytes());
            SESSIONS.put(recording, session.getFileName().toString());

            Path chunked = VolumeWriter.session(chunkedPod, recording.startedAt().minus(SESSION_BEFORE_RECORDING));
            layDownChunks(recording, archive, chunked, recording == newest);
            CHUNKED_SESSIONS.put(recording, chunked.getFileName().toString());
            CHUNKED_SESSION_DIRS.put(recording, chunked);
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

    /** The profile of a run's session of one file, downloaded whole and built the first time it is asked for. */
    String profile(PersonsRecording recording) {
        return profileOfSession(SESSIONS.get(recording));
    }

    /** The profile of a run's session of chunks, downloaded whole and built the first time it is asked for. */
    String chunkedProfile(PersonsRecording recording) {
        return profileOfSession(CHUNKED_SESSIONS.get(recording));
    }

    private String profileOfSession(String sessionId) {
        synchronized (PROFILES) {
            return PROFILES.computeIfAbsent(sessionId, id -> profileOf(
                    download(sessionRef(id), Json.createObject().put("window", "WHOLE"))));
        }
    }

    /**
     * async-profiler's chunks of a run, cut out of its file and named by when each began; the
     * header of each is checked against the start the fixture pins. Compressed as the hub leaves
     * them: every chunk, but the newest of the pod's newest session.
     */
    private static void layDownChunks(PersonsRecording recording, Path archive, Path session, boolean newestSession) {
        List<byte[]> chunks;
        try (InputStream in = Lz4Compressor.decompressStream(archive)) {
            chunks = JfrFileChunks.split(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<PersonsRecording.ProfilerChunk> profilerChunks = recording.profilerChunks();
        for (PersonsRecording.ProfilerChunk chunk : profilerChunks) {
            byte[] bytes = chunks.get(chunk.index());
            Instant header = JfrFileChunks.startOf(bytes).truncatedTo(ChronoUnit.SECONDS);
            if (!header.equals(chunk.openedAt())) {
                throw new IllegalStateException(recording + " chunk " + chunk.index() + " begins at " + header
                        + ", not at " + chunk.openedAt());
            }
            Path plain = VolumeWriter.recording(session, chunk.openedAt(), ZoneOffset.UTC, bytes);
            boolean keptPlain = newestSession && chunk == profilerChunks.getLast();
            if (!keptPlain) {
                Lz4Compressor.compress(plain, session.resolve(archiveName(chunk.openedAt())));
                try {
                    Files.delete(plain);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    private static String archiveName(Instant openedAt) {
        return VolumeWriter.recordingName(openedAt, ZoneOffset.UTC) + ARCHIVE_SUFFIX;
    }
}
