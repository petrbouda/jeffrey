package pbouda.jeffrey.repository;

import pbouda.jeffrey.WorkingDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

public class WorkingDirFlamegraphRepository implements FlamegraphRepository {

    private static final String DATA_FILE_EXTENSION = ".data";

    private final WorkingDirectory workingDir;

    public WorkingDirFlamegraphRepository(WorkingDirectory workingDir) {
        this.workingDir = workingDir;
    }

    @Override
    public List<FlamegraphFile> list(String profile) {
        try (Stream<Path> paths = Files.list(workingDir.generatedFlamegraphsDir(profile))) {
            return paths.filter(p -> p.getFileName().toString().endsWith(DATA_FILE_EXTENSION))
                    .map(WorkingDirFlamegraphRepository::toFile)
                    .toList();

        } catch (NotDirectoryException nde){
            return List.of();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read flamegraphs for a profile: " + profile, e);
        }
    }

    @Override
    public String content(String filename) {
        Path path = FLAMEGRAPHS_PATH.resolve(filename);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot read a file: path=\{path}", e);
        }
    }

    private static FlamegraphFile toFile(Path file) {
        try {
            Instant modificationTime = Files.getLastModifiedTime(file).toInstant();
            long sizeInBytes = Files.size(file);

            return new FlamegraphFile(file.getFileName().toString(), toDateTime(modificationTime), sizeInBytes);
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot get info about file: \{file}", e);
        }
    }

    private static LocalDateTime toDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }
}
