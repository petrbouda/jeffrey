/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

public class FileSystemRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemRepository.class);

    private static final String PROJECT_INFO_FILENAME = JeffreyLayout.PROJECT_INFO_FILE;
    private static final String INSTANCE_INFO_FILENAME = JeffreyLayout.INSTANCE_INFO_FILE;
    private static final String SESSION_INFO_FILENAME = JeffreyLayout.SESSION_INFO_FILE;

    private final Clock clock;

    public FileSystemRepository(Clock clock) {
        this.clock = clock;
    }

    public void addProject(
            String projectId,
            String projectName,
            Map<String, String> attributes,
            Path projectPath) {
        LOG.debug("Adding project to filesystem repository: projectId={} projectName={} projectPath={}", projectId, projectName, projectPath);
        try {
            RemoteProject project = new RemoteProject(
                    projectId,
                    projectName,
                    clock.instant().toEpochMilli(),
                    attributes);

            Path projectInfoFile = projectPath.resolve(PROJECT_INFO_FILENAME);
            writeAtomically(projectInfoFile, Json.toString(project));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write project info for project: " + projectId, e);
        }
    }

    public void addInstance(String instanceId, Path instancePath) {
        LOG.debug("Adding instance to filesystem repository: instanceId={} instancePath={}", instanceId, instancePath);
        try {
            RemoteProjectInstance instance = new RemoteProjectInstance(instanceId, clock.instant().toEpochMilli());

            Path instanceInfoFile = instancePath.resolve(INSTANCE_INFO_FILENAME);
            writeAtomically(instanceInfoFile, Json.toString(instance));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write instance info for instance: " + instanceId, e);
        }
    }

    public Optional<RemoteProjectInstance> findInstance(Path instancePath) {
        Path instanceInfoFile = instancePath.resolve(INSTANCE_INFO_FILENAME);
        if (Files.exists(instanceInfoFile)) {
            try {
                String jsonContent = Files.readString(instanceInfoFile);
                return Optional.of(Json.read(jsonContent, RemoteProjectInstance.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read instance info from: " + instanceInfoFile + ", error: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    public void addSession(String sessionId, Path sessionPath) {
        LOG.debug("Adding session to filesystem repository: sessionId={} sessionPath={}", sessionId, sessionPath);
        try {
            RemoteProjectInstanceSession session =
                    new RemoteProjectInstanceSession(sessionId, clock.instant().toEpochMilli());

            Path sessionInfoFile = sessionPath.resolve(SESSION_INFO_FILENAME);
            writeAtomically(sessionInfoFile, Json.toString(session));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write session info for session: " + sessionId + " at: " + sessionPath, e);
        }
    }

    public Optional<RemoteProject> findProject(Path projectPath) {
        Path projectInfoFile = projectPath.resolve(PROJECT_INFO_FILENAME);
        if (Files.exists(projectInfoFile)) {
            try {
                String jsonContent = Files.readString(projectInfoFile);
                return Optional.of(Json.read(jsonContent, RemoteProject.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to read project info from: " + projectInfoFile + ", error: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Marker files are the hub's only record of the volume, read whenever it is asked: it must
     * never observe a half-written file, so the content lands in a temp file first and is moved into
     * place atomically (with a plain move fallback for filesystems without atomic move).
     */
    private static void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
