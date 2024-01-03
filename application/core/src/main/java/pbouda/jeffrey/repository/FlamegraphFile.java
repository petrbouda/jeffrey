package pbouda.jeffrey.repository;

import java.time.LocalDateTime;

public record FlamegraphFile(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
