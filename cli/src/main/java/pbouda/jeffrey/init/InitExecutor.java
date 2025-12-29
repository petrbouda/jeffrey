package pbouda.jeffrey.init;

import pbouda.jeffrey.shared.IDGenerator;
import pbouda.jeffrey.shared.model.repository.RemoteProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Executes the initialization of a Jeffrey project and session.
 * Contains all business logic extracted from command classes.
 */
public class InitExecutor {

    private static final Clock CLOCK = Clock.systemUTC();

    private static final String DEFAULT_FILE_TEMPLATE = "profile-%t.jfr";
    private static final String ENV_FILE_NAME = ".env";
    private static final String WORKSPACES_DIR_NAME = "workspaces";
    private static final String JEFFREY_HOME_PROP = "JEFFREY_HOME";
    private static final String JEFFREY_WORKSPACES_PROP = "JEFFREY_WORKSPACES";
    private static final String JEFFREY_WORKSPACE_PROP = "JEFFREY_CURRENT_WORKSPACE";
    private static final String JEFFREY_SESSION_PROP = "JEFFREY_CURRENT_SESSION";
    private static final String JEFFREY_PROJECT_PROP = "JEFFREY_CURRENT_PROJECT";
    private static final String JEFFREY_FILE_PATTERN_PROP = "JEFFREY_FILE_PATTERN";
    private static final String JEFFREY_PROFILER_CONFIG_PROP = "JEFFREY_PROFILER_CONFIG";
    private static final String JDK_JAVA_OPTIONS_PROP = "JDK_JAVA_OPTIONS";

    /**
     * Executes the initialization with the given options.
     *
     * @param options validated initialization options
     * @throws Exception if initialization fails
     */
    public void execute(InitOptions options) throws Exception {
        Path jeffreyHome;
        Path workspacesPath;

        if (options.useJeffreyHome()) {
            jeffreyHome = createDirectories(Path.of(options.jeffreyHomePath()));
            workspacesPath = createDirectories(jeffreyHome.resolve(WORKSPACES_DIR_NAME));
        } else {
            workspacesPath = createDirectories(Path.of(options.workspacesDir()));
            jeffreyHome = null;
        }

        if (!workspacesPath.toFile().exists()) {
            throw new RuntimeException("Cannot create parent directories: " + workspacesPath);
        }

        Path workspacePath = createDirectories(workspacesPath.resolve(options.workspaceId()));

        FileSystemRepository repository = new FileSystemRepository(CLOCK);

        String projectId;
        Path projectPath = workspacePath.resolve(options.projectName());

        Optional<RemoteProject> projectOpt = repository.findProject(projectPath);
        if (projectOpt.isPresent()) {
            projectId = projectOpt.get().projectId();
        } else {
            projectId = IDGenerator.generate();

            createDirectories(projectPath);
            repository.addProject(
                    projectId,
                    options.projectName(),
                    options.projectLabel(),
                    options.workspaceId(),
                    options.workspacesDir(),
                    options.repositoryType(),
                    parseAttributes(options.attributes()),
                    projectPath);
        }

        String sessionId = IDGenerator.generate();
        Path newSessionPath = createDirectories(projectPath.resolve(sessionId));

        String features = new FeatureBuilder()
                .setHeapDumpEnabled(options.enableHeapDump())
                .setPerfCountersEnabled(options.enablePerfCounters())
                .setJvmLogging(options.enableJvmLogging())
                .setAdditionalJvmOptions(options.additionalJvmOptions())
                .build(newSessionPath);

        String profilerSettings = new ProfilerSettingsResolver(options.silent()).resolve(
                options.profilerPath(),
                options.profilerConfig(),
                workspacePath,
                options.projectName(),
                newSessionPath,
                features);

        repository.addSession(
                sessionId,
                projectId,
                options.workspaceId(),
                options.enablePerfCounters() ? FeatureBuilder.PERF_COUNTERS_FILE : null,
                profilerSettings,
                newSessionPath);

        String variables = variables(
                jeffreyHome,
                workspacesPath,
                workspacePath,
                projectPath,
                newSessionPath,
                profilerSettings,
                options.useJeffreyHome(),
                options.exportJdkJavaOptions());

        Path envFile = createEnvFile(projectPath, variables);
        if (!options.silent()) {
            System.out.println("# ENV file to with variables to source: ");
            System.out.println("# " + envFile);
            System.out.println(Files.readString(envFile));
        }
    }

    private static Path createEnvFile(Path projectPath, String variables) throws IOException {
        Path envFilePath = projectPath.resolve(ENV_FILE_NAME);
        return Files.writeString(envFilePath, variables);
    }

    private static String variables(
            Path jeffreyHome,
            Path workspacesPath,
            Path workspacePath,
            Path projectPath,
            Path sessionPath,
            String profilerSettings,
            boolean useJeffreyHome,
            boolean exportJdkJavaOptions) {

        String output = "";
        if (useJeffreyHome) {
            output += var(JEFFREY_HOME_PROP, jeffreyHome);
        }
        output += var(JEFFREY_WORKSPACES_PROP, workspacesPath);
        output += var(JEFFREY_WORKSPACE_PROP, workspacePath);
        output += var(JEFFREY_PROJECT_PROP, projectPath);
        output += var(JEFFREY_SESSION_PROP, sessionPath);
        output += var(JEFFREY_FILE_PATTERN_PROP, sessionPath.resolve(DEFAULT_FILE_TEMPLATE));
        if (profilerSettings != null && !profilerSettings.isEmpty()) {
            output += var(JEFFREY_PROFILER_CONFIG_PROP, wrapQuotes(profilerSettings), true);
            if (exportJdkJavaOptions) {
                output += var(JDK_JAVA_OPTIONS_PROP, wrapQuotes(profilerSettings), false);
            }
        }
        return output;
    }

    private static String var(String name, Path value) {
        return var(name, value.toString(), true);
    }

    private static String var(String name, String value, boolean addNewLine) {
        return "export " + name + "=" + value + (addNewLine ? "\n" : "");
    }

    private static Path createDirectories(Path path) throws IOException {
        return Files.exists(path) ? path : Files.createDirectories(path);
    }

    private static Map<String, String> parseAttributes(String[] keyValuePairs) {
        Map<String, String> attributes = new HashMap<>();
        if (keyValuePairs != null) {
            for (String attribute : keyValuePairs) {
                String trimmed = attribute.trim();
                String[] parts = trimmed.split("/", 2);
                if (parts.length == 2) {
                    attributes.put(parts[0].trim(), parts[1].trim());
                } else if (!trimmed.isEmpty()) {
                    System.err.println("[WARNING] Invalid attribute format: " + trimmed + " (expected: key/value)");
                }
            }
        }
        return attributes;
    }

    private static String wrapQuotes(String value) {
        return "'" + value + "'";
    }
}
