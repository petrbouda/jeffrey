package pbouda.jeffrey;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public abstract class FileUtils {

    public static Path createDirectoriesForFile(Path path) {
        createDirectories(path.getParent());
        return path;
    }

    public static Path createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                return Files.createDirectories(path);
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Cannot create parent directories: " + path);
        }
    }

    public static void removeDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Cannot complete removing of a directory: " + directory, e);
        }
    }
}
