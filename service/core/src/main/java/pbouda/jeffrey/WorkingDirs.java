package pbouda.jeffrey;

import java.nio.file.Path;

public class WorkingDirs {

    private final Path jeffreyDir;
    private final Path recordingsDir;
    private final Path exportsDir;

    public WorkingDirs(Path jeffreyDir) {
        this.jeffreyDir = jeffreyDir;
        this.recordingsDir = jeffreyDir.resolve("recordings");
        this.exportsDir = jeffreyDir.resolve("exports");
    }

    public void initializeDirectories() {
        FileUtils.createDirectories(jeffreyDir);
        FileUtils.createDirectories(recordingsDir);
        FileUtils.createDirectories(exportsDir);
    }

    public Path jeffreyDir() {
        return jeffreyDir;
    }

    public Path recordingsDir() {
        return recordingsDir;
    }

    public Path exportsDir() {
        return exportsDir;
    }
}
