package pbouda.jeffrey.repository;

import java.time.LocalDateTime;

public record ProfileFile(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
