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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.shared.common.HeartbeatConstants;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Writes a volume the way the provisioner, the heartbeat library and the hub's own workspace
 * creation do, so tests read what production reads: marker files, heartbeat files and recordings.
 */
public final class TestVolume {

    public static final Duration HEARTBEAT_THRESHOLD = Duration.ofSeconds(10);
    public static final Duration STARTUP_GRACE = Duration.ofMinutes(1);

    private TestVolume() {
    }

    public static SessionLiveness liveness(Clock clock) {
        return new SessionLiveness(clock, new FileHeartbeatReader(), new SessionDeadlines(HEARTBEAT_THRESHOLD, STARTUP_GRACE));
    }

    public static Path workspace(Path workspacesRoot, String id, String name, Instant createdAt) {
        Path dir = directories(workspacesRoot.resolve(id));
        VolumeMarkers.write(dir, WorkspaceMarker.FILE_NAME, new WorkspaceMarker(name, createdAt.toEpochMilli()));
        return dir;
    }

    public static Path project(Path workspaceDir, String projectId, String name, Instant createdAt) {
        Path dir = directories(workspaceDir.resolve(name));
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

    public static Path session(Path instanceDir, String sessionId, Instant createdAt) {
        Path dir = directories(instanceDir.resolve(sessionId));
        RemoteProjectInstanceSession marker = new RemoteProjectInstanceSession(sessionId, createdAt.toEpochMilli());
        write(dir.resolve(JeffreyLayout.SESSION_INFO_FILE), Json.toString(marker));
        return dir;
    }

    /** The heartbeat library's periodic beat. */
    public static void heartbeat(Path sessionDir, Instant at) {
        Path dir = directories(sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR));
        write(dir.resolve(HeartbeatConstants.HEARTBEAT_FILE), Long.toString(at.toEpochMilli()));
    }

    /** The heartbeat library's clean-exit marker. */
    public static void finished(Path sessionDir, Instant at) {
        Path dir = directories(sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR));
        write(dir.resolve(HeartbeatConstants.FINISHED_FILE), Long.toString(at.toEpochMilli()));
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
