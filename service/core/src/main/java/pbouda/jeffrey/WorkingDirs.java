package pbouda.jeffrey;

import java.nio.file.Path;

public class WorkingDirs {

    private final Path jeffreyDir;
    private final Path recordingsDir;

    public WorkingDirs(Path jeffreyDir) {
        this.jeffreyDir = jeffreyDir;
        this.recordingsDir = jeffreyDir.resolve("recordings");
    }

    public void initializeDirectories() {
        FileUtils.createDirectories(jeffreyDir);
        FileUtils.createDirectories(recordingsDir);
    }

    public Path jeffreyDir() {
        return jeffreyDir;
    }

    public Path recordingsDir() {
        return recordingsDir;
    }
}
