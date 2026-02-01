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

import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Executes the initialization of a Jeffrey project and session.
 * Contains all business logic extracted from command classes.
 */
public class InitExecutor {

    private static final Clock CLOCK = Clock.systemUTC();

    private static final String ENV_FILE_NAME = ".env";
    private static final String WORKSPACES_DIR_NAME = "workspaces";

    private final EnvFileBuilder envFileBuilder = new EnvFileBuilder();

    /**
     * Executes the initialization with the given configuration.
     *
     * @param config validated initialization configuration
     * @throws Exception if initialization fails
     */
    public void execute(InitConfig config) throws Exception {
        Path jeffreyHome;
        Path workspacesPath;

        if (config.useJeffreyHome()) {
            jeffreyHome = createDirectories(Path.of(config.getJeffreyHome()));
            workspacesPath = createDirectories(jeffreyHome.resolve(WORKSPACES_DIR_NAME));
        } else {
            workspacesPath = createDirectories(Path.of(config.getWorkspacesDir()));
            jeffreyHome = null;
        }

        if (!workspacesPath.toFile().exists()) {
            throw new RuntimeException("Cannot create parent directories: " + workspacesPath);
        }

        Path workspacePath = createDirectories(workspacesPath.resolve(config.getWorkspaceId()));

        FileSystemRepository repository = new FileSystemRepository(CLOCK);

        String projectId;
        Path projectPath = workspacePath.resolve(config.getProjectName());

        Optional<RemoteProject> projectOpt = repository.findProject(projectPath);
        if (projectOpt.isPresent()) {
            projectId = projectOpt.get().projectId();
        } else {
            projectId = IDGenerator.generate();

            createDirectories(projectPath);
            repository.addProject(
                    projectId,
                    config.getProjectName(),
                    config.getProjectLabel(),
                    config.getWorkspaceId(),
                    config.getWorkspacesDir(),
                    config.resolveRepositoryType(),
                    config.getAttributes(),
                    projectPath);
        }

        // Create instance folder (from config, HOSTNAME env var, or generated UUID)
        String instanceId = config.getInstanceId();
        Path instancePath = projectPath.resolve(instanceId);
        Optional<RemoteProjectInstance> instanceOpt = repository.findInstance(instancePath);
        if (instanceOpt.isEmpty()) {
            createDirectories(instancePath);
            repository.addInstance(
                    instanceId,
                    projectId,
                    config.getWorkspaceId(),
                    instancePath);
        }

        String sessionId = IDGenerator.generate();
        Path newSessionPath = createDirectories(instancePath.resolve(sessionId));

        if (config.isMessagingEnabled()) {
            createDirectories(newSessionPath.resolve(FeatureBuilder.STREAMING_REPO_DIR));
        }

        String features = new FeatureBuilder()
                .setHeapDumpEnabled(config.resolveHeapDumpType())
                .setPerfCountersEnabled(config.isPerfCountersEnabled())
                .setJvmLogging(config.getJvmLoggingCommand())
                .setMessagingEnabled(config.isMessagingEnabled())
                .setMessagingMaxAge(config.getMessagingMaxAge())
                .setAdditionalJvmOptions(config.getAdditionalJvmOptions())
                .build(newSessionPath);

        String profilerSettings = new ProfilerSettingsResolver().resolve(
                config.getProfilerPath(),
                config.getProfilerConfig(),
                workspacePath,
                config.getProjectName(),
                newSessionPath,
                features);

        // Calculate order: find max order from existing sessions + 1
        List<RemoteProjectInstanceSession> existingSessions = repository.findSessionsInInstance(instancePath);
        int maxOrder = existingSessions.stream()
                .mapToInt(RemoteProjectInstanceSession::order)
                .max()
                .orElse(0);
        int order = maxOrder + 1;

        repository.addSession(
                sessionId,
                projectId,
                config.getWorkspaceId(),
                instanceId,
                order,
                config.isPerfCountersEnabled() ? FeatureBuilder.PERF_COUNTERS_FILE : null,
                profilerSettings,
                config.isMessagingEnabled(),
                newSessionPath);

        EnvFileBuilder.Context envContext = new EnvFileBuilder.Context(
                jeffreyHome,
                workspacesPath,
                workspacePath,
                projectPath,
                newSessionPath,
                profilerSettings,
                config.useJeffreyHome(),
                config.isJdkJavaOptionsEnabled());
        String variables = envFileBuilder.build(envContext);

        Path envFile = createEnvFile(projectPath, variables);
        if (!config.isSilent()) {
            System.out.println("# ENV file to with variables to source: ");
            System.out.println("# " + envFile);
            System.out.println(Files.readString(envFile));
        }
    }

    private static Path createEnvFile(Path projectPath, String variables) throws IOException {
        Path envFilePath = projectPath.resolve(ENV_FILE_NAME);
        return Files.writeString(envFilePath, variables);
    }

    private static Path createDirectories(Path path) throws IOException {
        return Files.exists(path) ? path : Files.createDirectories(path);
    }
}
