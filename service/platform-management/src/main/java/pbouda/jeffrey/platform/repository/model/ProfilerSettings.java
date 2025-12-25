package pbouda.jeffrey.platform.repository.model;

import java.util.Map;

public record ProfilerSettings(String defaultSettings, Map<String, String> projectSettings) {
}
