package pbouda.jeffrey.repository.model;

import java.util.Map;

public record ProfilerSettings(
        String workspaceSettings,
        Map<String, String> projectSettings) {
}
