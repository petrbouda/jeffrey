package pbouda.jeffrey.init;

import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteSession;
import pbouda.jeffrey.shared.common.model.RepositoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

public class FileSystemRepository {

    private static final String PROJECT_INFO_FILENAME = ".project-info.json";
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

    public void addSession(
            String sessionId,
            String projectId,
            String workspaceId,
            String finishedFile,
            String profilerSettings,
            boolean streamingEnabled,
            Path sessionPath) {
        try {
            RemoteSession session = new RemoteSession(
                    sessionId,
                    projectId,
                    workspaceId,
                    clock.instant().toEpochMilli(),
                    finishedFile,
                    sessionId,
                    profilerSettings,
                    streamingEnabled);

            Path sessionInfoFile = sessionPath.resolve(SESSION_INFO_FILENAME);
            Files.writeString(sessionInfoFile, Json.toString(session));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write session info for session: " + sessionId + " in project: " + projectId, e);
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
