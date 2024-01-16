package pbouda.jeffrey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkingDirectory {

    private static final String HEATMAPS_DIR_NAME = "heatmaps";
    private static final String FLAMEGRAPHS_DIR_NAME = "flamegraphs";

    private static final Path USER_HOME_DIR = Path.of(System.getProperty("user.home"));

    private final Path profilesDir;
    private final Path generatedDir;

    public WorkingDirectory() {
        this(USER_HOME_DIR.resolve(".jeffrey"));
    }

    public WorkingDirectory(Path homeDir) {
        this.profilesDir = homeDir.resolve("profiles");
        this.generatedDir = homeDir.resolve("generated");
    }

    public void prepareDirectoryStructure() {
        try {
            Files.createDirectories(profilesDir);
            Files.createDirectories(generatedDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot prepare directory structure for Jeffrey", e);
        }
    }

    public Path profilesDir() {
        return profilesDir;
    }

    public Path generatedDir() {
        return generatedDir;
    }

    public Path profilePath(String profile) {
        return profilesDir.resolve(Naming.directoryName(profile));
    }

    public Path generatedDir(String profile) {
        return generatedDir.resolve(Naming.directoryName(profile));
    }

    public Path generatedFlamegraphsDir(String profile) {
        return generatedDir(Naming.directoryName(profile)).resolve(FLAMEGRAPHS_DIR_NAME);
    }

    public Path generatedHeatmapsDir(String profile) {
        return generatedDir(Naming.directoryName(profile)).resolve(HEATMAPS_DIR_NAME);
    }
}
