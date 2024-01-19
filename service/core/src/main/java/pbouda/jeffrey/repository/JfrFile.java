package pbouda.jeffrey.repository;

import java.time.LocalDateTime;

public record JfrFile(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
