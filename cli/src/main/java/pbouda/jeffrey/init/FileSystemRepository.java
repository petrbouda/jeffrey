/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class FileSystemRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemRepository.class);

    private static final String PROJECT_INFO_FILENAME = ".project-info.json";
    private static final String INSTANCE_INFO_FILENAME = ".instance-info.json";
    private static final String SESSION_INFO_FILENAME = ".session-info.json";

    private final Clock clock;

    public FileSystemRepository(Clock clock) {
        this.clock = clock;
    }

    public void addProject(
            String projectId,
            String projectName,
            String projectLabel,
            String workspaceId,
            String workspacesDir,
            RepositoryType repositoryType,
            Map<String, String> attributes,
            Path projectPath) {
        LOG.debug("Adding project to filesystem repository: projectId={} projectName={} projectPath={}", projectId, projectName, projectPath);
        try {
            RemoteProject project = new RemoteProject(
                    projectId,
                    projectName,
                    projectLabel,
                    workspaceId,
                    clock.instant().toEpochMilli(),
                    workspacesDir,
                    workspaceId,
                    projectName,
                    repositoryType,
                    attributes);

            Path projectInfoFile = projectPath.resolve(PROJECT_INFO_FILENAME);
            Files.writeString(projectInfoFile, Json.toString(project));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write project info for project: " + projectId, e);
        }
    }

    public void addInstance(
            String instanceId,
            String projectId,
            String workspaceId,
            Path instancePath) {
        LOG.debug("Adding instance to filesystem repository: instanceId={} projectId={} instancePath={}", instanceId, projectId, instancePath);
        try {
            RemoteProjectInstance instance = new RemoteProjectInstance(
                    instanceId,
                    projectId,
                    workspaceId,
                    clock.instant().toEpochMilli(),
                    instanceId);

            Path instanceInfoFile = instancePath.resolve(INSTANCE_INFO_FILENAME);
            Files.writeString(instanceInfoFile, Json.toString(instance));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write instance info for instance: " + instanceId + " in project: " + projectId, e);
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

    public void addSession(
            String sessionId,
            String projectId,
            String workspaceId,
            String instanceId,
            int order,
            String profilerSettings,
            boolean streamingEnabled,
            Path sessionPath) {
        LOG.debug("Adding session to filesystem repository: sessionId={} projectId={} instanceId={} sessionPath={}", sessionId, projectId, instanceId, sessionPath);
        try {
            // Build relative session path: instanceId/sessionId (instance is always required)
            String relativeSessionPath = instanceId + "/" + sessionId;

            RemoteProjectInstanceSession session = new RemoteProjectInstanceSession(
                    sessionId,
                    projectId,
                    workspaceId,
                    instanceId,
                    clock.instant().toEpochMilli(),
                    order,
                    relativeSessionPath,
                    profilerSettings,
                    streamingEnabled);

            Path sessionInfoFile = sessionPath.resolve(SESSION_INFO_FILENAME);
            Files.writeString(sessionInfoFile, Json.toString(session));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write session info for session: " + sessionId + " in project: " + projectId, e);
        }
    }

    public List<RemoteProjectInstanceSession> findSessionsInInstance(Path instancePath) {
        if (!Files.exists(instancePath) || !Files.isDirectory(instancePath)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(instancePath)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(sessionDir -> sessionDir.resolve(SESSION_INFO_FILENAME))
                    .filter(Files::exists)
                    .map(sessionInfoFile -> {
                        try {
                            String jsonContent = Files.readString(sessionInfoFile);
                            return Json.read(jsonContent, RemoteProjectInstanceSession.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to read session info from: " + sessionInfoFile, e);
                        }
                    })
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list sessions in instance: " + instancePath, e);
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
}
