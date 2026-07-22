package cafe.jeffrey.provisioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.CliConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettingsSource;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ProfilerSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsResolver.class);

    private static final String WORKSPACE_SETTINGS_PREFIX = JeffreyLayout.SETTINGS_FILE_PREFIX;
    private static final String WORKSPACE_SETTINGS_DIR = JeffreyLayout.SETTINGS_DIR;

    private static final String HUB_WORKSPACE_LEVEL = "WORKSPACE";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern(JeffreyLayout.SETTINGS_TIMESTAMP_PATTERN).withZone(ZoneOffset.UTC);

    private static final Comparator<Path> TIMESTAMP_FILE_COMPARATOR =
            Comparator.comparing((Path path) -> {
                String filename = path.toString();
                String substring = filename.substring(filename.indexOf('-') + 1, filename.lastIndexOf('.'));
                return Instant.from(TIMESTAMP_FORMATTER.parse(substring));
            }).reversed();

    /**
     * The resolved profiler command together with its provenance: which source won
     * ({@code source}) and, for hub-pushed settings, the settings file it came from
     * ({@code sourceDetail}, null otherwise).
     */
    public record ResolvedProfilerSettings(String command, ProfilerSettingsSource source, String sourceDetail) {
    }

    public ResolvedProfilerSettings resolve(
            String profilerPath,
            String profilerConfig,
            Path workspacePath,
            String projectId,
            String projectName,
            Path currentSessionPath,
            String features) {

        ResolvedProfilerSettings resolved;
        // Directly provided ProfilerConfig has priority over workspace settings
        if (profilerConfig != null && !profilerConfig.isBlank()) {
            resolved = new ResolvedProfilerSettings(profilerConfig, ProfilerSettingsSource.CLI_CONFIG, null);
        } else {
            resolved = resolveJeffreyProfilerConfig(workspacePath, projectId, projectName);
        }

        LOG.info("Profiler config resolved: source={} settings_file={}", resolved.source(), resolved.sourceDetail());

        String command = replacePlaceholders(resolved.command(), profilerPath, currentSessionPath) + " " + features;
        return new ResolvedProfilerSettings(command, resolved.source(), resolved.sourceDetail());
    }

    private static String replacePlaceholders(String config, String profilerPath, Path sessionPath) {
        if (config == null || config.isBlank()) {
            return "";
        }

        return config
                .replace(CliConstants.PROFILER_PATH, profilerPath == null ? "" : profilerPath)
                .replace(CliConstants.CURRENT_SESSION, sessionPath.toString());
    }

    private ResolvedProfilerSettings resolveJeffreyProfilerConfig(Path workspacePath, String projectId, String projectName) {
        try {
            Path settingsDir = Files.createDirectories(workspacePath.resolve(WORKSPACE_SETTINGS_DIR));
            List<Path> settingsFiles = getSettingsFiles(settingsDir);
            if (settingsFiles.isEmpty()) {
                return new ResolvedProfilerSettings(CliConstants.DEFAULT_PROFILER_CONFIG, ProfilerSettingsSource.BUILT_IN, null);
            }

            Path settingsFile = settingsFiles.getFirst();
            String settingsFilename = settingsFile.getFileName().toString();
            RemoteWorkspaceSettings settings = readSettings(settingsFile);
            ProfilerSettings profilerSettings = settings.profiler();

            // Prefer the id-keyed lookup — project names are mutable and can collide;
            // the name-keyed map remains as the fallback for settings files written
            // by older hubs that only publish names.
            String projectConfig = projectSettingsFor(profilerSettings, projectId, projectName);
            if (projectConfig != null && !projectConfig.isBlank()) {
                return new ResolvedProfilerSettings(projectConfig, ProfilerSettingsSource.HUB_PROJECT, settingsFilename);
            }

            if (profilerSettings.defaultSettings() != null && !profilerSettings.defaultSettings().isBlank()) {
                ProfilerSettingsSource source = HUB_WORKSPACE_LEVEL.equalsIgnoreCase(profilerSettings.defaultSettingsLevel())
                        ? ProfilerSettingsSource.HUB_WORKSPACE
                        : ProfilerSettingsSource.HUB_GLOBAL;
                return new ResolvedProfilerSettings(profilerSettings.defaultSettings(), source, settingsFilename);
            }

            return new ResolvedProfilerSettings(CliConstants.DEFAULT_PROFILER_CONFIG, ProfilerSettingsSource.BUILT_IN, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String projectSettingsFor(ProfilerSettings profilerSettings, String projectId, String projectName) {
        Map<String, String> byId = profilerSettings.projectSettingsById();
        if (byId != null && projectId != null) {
            String byIdConfig = byId.get(projectId);
            if (byIdConfig != null && !byIdConfig.isBlank()) {
                return byIdConfig;
            }
        }

        Map<String, String> byName = profilerSettings.projectSettings();
        return byName != null ? byName.get(projectName) : null;
    }

    private static RemoteWorkspaceSettings readSettings(Path settingsFile) {
        try {
            String content = Files.readString(settingsFile);
            return Json.read(content, RemoteWorkspaceSettings.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read profiler settings file: " + settingsFile, e);
        }
    }

    private static List<Path> getSettingsFiles(Path settingsDir) {
        return FileSystemUtils.allFilesInDirectory(settingsDir).stream()
                .filter(path -> {
                    String filename = path.getFileName().toString();
                    return filename.startsWith(WORKSPACE_SETTINGS_PREFIX) && filename.endsWith(".json");
                })
                .sorted(TIMESTAMP_FILE_COMPARATOR)
                .toList();
    }
}
