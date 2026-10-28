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

package cafe.jeffrey.test.volume;

import cafe.jeffrey.shared.common.JeffreyLayout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Writes a recording volume the way the provisioner and the profiler do — directories named for
 * what they hold, and recordings named for when they were opened and in which zone — so every
 * reader of a volume (the hub, the shared layout, Microscope reading storage directly) is tested
 * against what production writes. Nothing below a workspace has a marker file.
 */
public final class VolumeWriter {

    /** Where a time-ordered UUID keeps its epoch millis: above the version and counter bits. */
    private static final int UUID_TIMESTAMP_SHIFT = 16;
    private static final long UUID_VERSION_7 = 0x7000L;
    private static final long UUID_STANDARD_VARIANT = 0x8000_0000_0000_0000L;

    /** async-profiler's {@code %t}: the machine's local time. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String RECORDING_TEMPLATE = "profile-%t.jfr";

    private VolumeWriter() {
    }

    /** A project's directory, named {@code name} — its id — under a workspace's directory. */
    public static Path project(Path workspaceDir, String name) {
        return directories(workspaceDir.resolve(name));
    }

    /** An instance's directory, named {@code instanceId}, in a project's directory. */
    public static Path instance(Path projectDir, String instanceId) {
        return directories(projectDir.resolve(instanceId));
    }

    /** A session's directory, named as the provisioner names a session started at {@code start}. */
    public static Path session(Path instanceDir, Instant start) {
        return directories(instanceDir.resolve(sessionIdAt(start)));
    }

    /**
     * The name the provisioner gives a session started at {@code start}: a time-ordered UUID
     * carrying it, which is what an instance's sessions are ordered by.
     */
    public static String sessionIdAt(Instant start) {
        long mostSignificant = (start.toEpochMilli() << UUID_TIMESTAMP_SHIFT) | UUID_VERSION_7;
        return new UUID(mostSignificant, UUID_STANDARD_VARIANT).toString();
    }

    /**
     * The name async-profiler gives a recording opened at {@code openedAt} on a machine in
     * {@code zone}, through the provisioner's template: {@code profile-20260220-120500.UTC.jfr}.
     */
    public static String recordingName(Instant openedAt, ZoneId zone) {
        String template = JeffreyLayout.recordingTemplate(RECORDING_TEMPLATE, zone);
        String local = LocalDateTime.ofInstant(openedAt, zone).format(TIMESTAMP);
        return template.replace(JeffreyLayout.TIMESTAMP_PLACEHOLDER, local);
    }

    /** A recording opened at {@code openedAt} on a machine in {@code zone}. */
    public static Path recording(Path sessionDir, Instant openedAt, ZoneId zone, String content) {
        return file(sessionDir, recordingName(openedAt, zone), content);
    }

    public static Path file(Path dir, String name, String content) {
        Path file = dir.resolve(name);
        write(file, content);
        return file;
    }

    private static Path directories(Path dir) {
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
