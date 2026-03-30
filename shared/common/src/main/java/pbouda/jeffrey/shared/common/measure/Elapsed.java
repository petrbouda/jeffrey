package pbouda.jeffrey.shared.common.measure;

import java.time.Duration;

public record Elapsed<T>(Duration duration, T entity) {
}
