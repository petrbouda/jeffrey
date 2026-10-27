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
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a recording volume the way the provisioner and the profiler do — marker files and
 * recordings — so every reader of a volume (the hub, the shared layout,
 * Microscope reading storage directly) is tested against what production writes.
 */
public final class VolumeWriter {

    /** Where a time-ordered UUID keeps its epoch millis: above the version and counter bits. */
    private static final int UUID_TIMESTAMP_SHIFT = 16;
    private static final long UUID_VERSION_7 = 0x7000L;
    private static final long UUID_STANDARD_VARIANT = 0x8000_0000_0000_0000L;

    private VolumeWriter() {
    }

    /** A project directory named {@code name} under {@code parent}, with its marker. */
    public static Path project(Path parent, String projectId, String name, Instant createdAt) {
        Path dir = directories(parent.resolve(name));
        RemoteProject marker = new RemoteProject(projectId, name, createdAt.toEpochMilli(), Map.of("env", "test"));
        write(dir.resolve(JeffreyLayout.PROJECT_INFO_FILE), Json.toString(marker));
        return dir;
    }

    public static Path instance(Path projectDir, String instanceId, Instant createdAt) {
        Path dir = directories(projectDir.resolve(instanceId));
        RemoteProjectInstance marker = new RemoteProjectInstance(instanceId, createdAt.toEpochMilli());
        write(dir.resolve(JeffreyLayout.INSTANCE_INFO_FILE), Json.toString(marker));
        return dir;
    }

    /** A session whose marker records the time zone its machine runs in; {@code null} for none. */
    public static Path session(Path instanceDir, String sessionId, Instant createdAt, String zoneId) {
        Path dir = directories(instanceDir.resolve(sessionId));
        RemoteProjectInstanceSession marker =
                new RemoteProjectInstanceSession(sessionId, createdAt.toEpochMilli(), zoneId);
        write(dir.resolve(JeffreyLayout.SESSION_INFO_FILE), Json.toString(marker));
        return dir;
    }

    /**
     * The name the provisioner gives a session started at {@code start}: a time-ordered UUID
     * carrying it, which is what an instance's sessions are ordered by.
     */
    public static String sessionIdAt(Instant start) {
        long mostSignificant = (start.toEpochMilli() << UUID_TIMESTAMP_SHIFT) | UUID_VERSION_7;
        return new UUID(mostSignificant, UUID_STANDARD_VARIANT).toString();
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
