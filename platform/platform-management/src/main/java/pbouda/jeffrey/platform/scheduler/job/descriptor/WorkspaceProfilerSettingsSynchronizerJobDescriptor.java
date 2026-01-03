package pbouda.jeffrey.platform.scheduler.job.descriptor;

import org.springframework.core.env.PropertyResolver;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.util.Map;

public record WorkspaceProfilerSettingsSynchronizerJobDescriptor(
        int maxVersions
) implements JobDescriptor<WorkspaceProfilerSettingsSynchronizerJobDescriptor> {

    private static final String PARAM_MAX_VERSIONS = "maxVersions";

    @Override
    public Map<String, String> params() {
        return Map.of(PARAM_MAX_VERSIONS, Integer.toString(maxVersions));
    }

    @Override
    public JobType type() {
        return JobType.WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER;
    }

    public static WorkspaceProfilerSettingsSynchronizerJobDescriptor of(PropertyResolver properties) {
        Integer maxVersions = properties.getRequiredProperty(
                "jeffrey.job.profiler-settings-synchronizer.max-versions", Integer.class);
        return new WorkspaceProfilerSettingsSynchronizerJobDescriptor(maxVersions);
    }

    public static WorkspaceProfilerSettingsSynchronizerJobDescriptor of(Map<String, String> params) {
        return new WorkspaceProfilerSettingsSynchronizerJobDescriptor(
                JobDescriptorUtils.resolveInt(params, PARAM_MAX_VERSIONS));
    }
}
