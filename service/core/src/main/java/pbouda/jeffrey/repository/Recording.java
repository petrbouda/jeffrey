package pbouda.jeffrey.repository;

import java.time.LocalDateTime;

public record Recording(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
