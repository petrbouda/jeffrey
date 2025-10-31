package pbouda.jeffrey.provider.api;

import java.util.Map;

public record PersistenceProperties(
        Map<String, String> core,
        Map<String, String> events) {
}
