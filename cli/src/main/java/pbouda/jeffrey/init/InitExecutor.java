package pbouda.jeffrey.init;

import pbouda.jeffrey.shared.IDGenerator;
import pbouda.jeffrey.shared.model.repository.RemoteProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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

        String sessionId = IDGenerator.generate();
        Path newSessionPath = createDirectories(projectPath.resolve(sessionId));

        if (config.isMessagingEnabled()) {
            createDirectories(newSessionPath.resolve(FeatureBuilder.MESSAGING_REPO_DIR));
        }

        String features = new FeatureBuilder()
                .setHeapDumpEnabled(config.resolveHeapDumpType())
                .setPerfCountersEnabled(config.isPerfCountersEnabled())
                .setJvmLogging(config.getJvmLoggingCommand())
                .setMessagingEnabled(config.isMessagingEnabled())
                .setMessagingMaxAge(config.getMessagingMaxAge())
                .setAdditionalJvmOptions(config.getAdditionalJvmOptions())
                .build(newSessionPath);

        String profilerSettings = new ProfilerSettingsResolver(config.isSilent()).resolve(
                config.getProfilerPath(),
                config.getProfilerConfig(),
                workspacePath,
                config.getProjectName(),
                newSessionPath,
                features);

        repository.addSession(
                sessionId,
                projectId,
                config.getWorkspaceId(),
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
