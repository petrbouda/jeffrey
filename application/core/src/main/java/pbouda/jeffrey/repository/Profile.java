package pbouda.jeffrey.repository;

import java.time.LocalDateTime;

public record Profile(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
