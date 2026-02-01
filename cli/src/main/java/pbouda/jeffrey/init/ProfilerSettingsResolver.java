package pbouda.jeffrey.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.ProfilerSettings;
import pbouda.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class ProfilerSettingsResolver {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsResolver.class);

    private static final String WORKSPACE_SETTINGS_PREFIX = "settings-";
    private static final String WORKSPACE_SETTINGS_DIR = ".settings";

    private static final String DEFAULT_PROFILER_CONFIG =
            "-agentpath:" + Replacements.PROFILER_PATH + "=start,alloc,lock,event=ctimer,jfrsync=default,loop=15m,chunksize=5m,file="
                    + Replacements.CURRENT_SESSION + "/profile-%t.jfr";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssSSSSSS").withZone(ZoneOffset.UTC);

    private static final Comparator<Path> TIMESTAMP_FILE_COMPARATOR =
            Comparator.comparing((Path path) -> {
                String filename = path.toString();
                String substring = filename.substring(filename.indexOf('-') + 1, filename.lastIndexOf('.'));
                return Instant.from(TIMESTAMP_FORMATTER.parse(substring));
            }).reversed();

    public String resolve(
            String profilerPath,
            String profilerConfig,
            Path workspacePath,
            String projectName,
            Path currentSessionPath,
            String features) {

        String config;
        // Directly provided ProfilerConfig has priority over workspace settings
        if (profilerConfig != null && !profilerConfig.isBlank()) {
            config = profilerConfig;
            LOG.info("Profiler config resolved from: --profiler-config option");
        } else {
            config = resolveJeffreyProfilerConfig(workspacePath, projectName);
        }

        return replacePlaceholders(config, profilerPath, currentSessionPath) + " " + features;
    }

    private static String replacePlaceholders(String config, String profilerPath, Path sessionPath) {
        if (config == null) {
            return null;
        }

        return config
                .replace(Replacements.PROFILER_PATH, profilerPath == null ? "" : profilerPath)
                .replace(Replacements.CURRENT_SESSION, sessionPath.toString());
    }

    private String resolveJeffreyProfilerConfig(Path workspacePath, String projectName) {
        try {
            Path settingsDir = Files.createDirectories(workspacePath.resolve(WORKSPACE_SETTINGS_DIR));
            List<Path> settingsFiles = getSettingsFiles(settingsDir);
            if (!settingsFiles.isEmpty()) {
                Path settingsFile = settingsFiles.getFirst();
                RemoteWorkspaceSettings settings = readSettings(settingsFile);
                ProfilerSettings profilerSettings = settings.profiler();

                String projectConfig = profilerSettings.projectSettings().get(projectName);
                if (projectConfig != null) {
                    LOG.info("Profiler config resolved from: {} (project: {})", settingsFile, projectName);
                    return projectConfig;
                } else {
                    LOG.info("Profiler config resolved from: {} (default section)", settingsFile);
                    return profilerSettings.defaultSettings();
                }
            } else {
                LOG.info("Profiler config resolved from: built-in default configuration");
                return DEFAULT_PROFILER_CONFIG;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
