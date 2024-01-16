package pbouda.jeffrey.info;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record ProfileInfo(
        String id,
        LocalDateTime createdAt,
        Path dataDir,
        Path originalJfrPath) {
}
