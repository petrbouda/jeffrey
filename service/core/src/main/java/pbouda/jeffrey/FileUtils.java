package pbouda.jeffrey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class FileUtils {

    public static Path createDirectoriesForFile(Path path) {
        try {
            Path directories = path.getParent();
            if (!Files.exists(directories)) {
                return Files.createDirectories(directories);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("");
        }
    }
}
