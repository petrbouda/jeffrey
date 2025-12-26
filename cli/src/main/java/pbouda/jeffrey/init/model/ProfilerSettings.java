package pbouda.jeffrey.init.model;

import java.util.Map;

public record ProfilerSettings(String defaultSettings, Map<String, String> projectSettings) {
}
