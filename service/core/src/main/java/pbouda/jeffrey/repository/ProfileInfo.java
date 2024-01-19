package pbouda.jeffrey.repository;

import java.nio.file.Path;
import java.time.Instant;

public record ProfileInfo(String id, String name, Instant createdAt, Path profilePath) {
}
