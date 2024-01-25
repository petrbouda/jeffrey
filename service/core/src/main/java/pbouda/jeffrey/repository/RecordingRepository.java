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

public class RecordingRepository {

    private final WorkingDirs workingDirs;

    public RecordingRepository(WorkingDirs workingDirs) {
        this.workingDirs = workingDirs;
    }

    public List<Recording> all() {
        try (Stream<Path> paths = Files.list(workingDirs.recordingsDir())) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".jfr"))
                    .map(RecordingRepository::toProfile)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException("Cannot read profiles: " + workingDirs.recordingsDir(), e);
        }
    }

    private static Recording toProfile(Path file) {
        try {
            Instant modificationTime = Files.getLastModifiedTime(file).toInstant();
            long sizeInBytes = Files.size(file);

            return new Recording(file.getFileName().toString(), toDateTime(modificationTime), sizeInBytes);
        } catch (IOException e) {
            throw new RuntimeException("Cannot get info about profile: " + file, e);
        }
    }

    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }
}
