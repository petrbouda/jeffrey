package pbouda.jeffrey.repository.model;

import java.time.Instant;

public record ProfileInfo(String id, String name, Instant createdAt, Instant startedAt) {
}
