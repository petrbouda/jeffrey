package pbouda.jeffrey;

import java.nio.file.Path;

public class WorkingDirs {

    private final Path jeffreyDir;
    private final Path profilesDir;

    public WorkingDirs(Path jeffreyDir) {
        this.jeffreyDir = jeffreyDir;
        this.profilesDir = jeffreyDir.resolve("profiles");
    }

    public Path jeffreyDir() {
        return jeffreyDir;
    }

    public Path profilesDir() {
        return profilesDir;
    }
}
