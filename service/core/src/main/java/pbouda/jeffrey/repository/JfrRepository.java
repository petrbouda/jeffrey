package pbouda.jeffrey.repository;

import pbouda.jeffrey.WorkingDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

public class JfrRepository {

    private final WorkingDirs workingDirs;

    public JfrRepository(WorkingDirs workingDirs) {
        this.workingDirs = workingDirs;
    }

    public List<JfrFile> all() {
        try (Stream<Path> paths = Files.list(workingDirs.profilesDir())) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".jfr"))
                    .map(JfrRepository::toProfile)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException("Cannot read profiles: " + workingDirs.profilesDir(), e);
        }
    }

    private static JfrFile toProfile(Path file) {
        try {
            Instant modificationTime = Files.getLastModifiedTime(file).toInstant();
            long sizeInBytes = Files.size(file);

            return new JfrFile(file.getFileName().toString(), toDateTime(modificationTime), sizeInBytes);
        } catch (IOException e) {
            throw new RuntimeException("Cannot get info about profile: " + file, e);
        }
    }

    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }
}
