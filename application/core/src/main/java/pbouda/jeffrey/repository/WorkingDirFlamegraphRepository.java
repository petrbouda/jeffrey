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

public class WorkingDirFlamegraphRepository implements FlamegraphRepository {

    private static final Path FLAMEGRAPHS_PATH = WorkingDirectory.GENERATED_DIR;

    @Override
    public List<FlamegraphFile> list() {
        try (Stream<Path> paths = Files.list(FLAMEGRAPHS_PATH)) {
            return paths.filter(p -> p.getFileName().toString().endsWith(".html"))
                    .map(WorkingDirFlamegraphRepository::toFile)
                    .toList();

        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot read flamegraphs: \{FLAMEGRAPHS_PATH}", e);
        }
    }

    @Override
    public String content(String filename) {
        Path path = FLAMEGRAPHS_PATH.resolve(filename);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot read a flamegraph file: path=\{path}", e);
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
