package pbouda.jeffrey.repository.model;

import java.time.LocalDateTime;

public record Recording(String filename, LocalDateTime dateTime, long sizeInBytes) {
}
