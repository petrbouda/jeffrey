package pbouda.jeffrey.repository;

import pbouda.jeffrey.WorkingDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

public class WorkingDirProfileRepository implements ProfileRepository {

    private static final Path PROFILES_PATH = WorkingDirectory.PATH.resolve("profiles");

    @Override
    public List<Profile> list() {
        try (Stream<Path> paths = Files.list(PROFILES_PATH)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".jfr"))
                    .map(WorkingDirProfileRepository::toProfile)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot read profiles: \{PROFILES_PATH}", e);
        }
    }

    private static Profile toProfile(Path file) {
        try {
            Instant modificationTime = Files.getLastModifiedTime(file).toInstant();
            long sizeInBytes = Files.size(file);

            return new Profile(file.getFileName().toString(), toDateTime(modificationTime), sizeInBytes);
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot get info about profile: \{file}", e);
        }
    }

    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }
}
