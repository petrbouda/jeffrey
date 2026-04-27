package cafe.jeffrey.server.core.scheduler.job;

import cafe.jeffrey.shared.common.model.ProfilerInfo;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.server.core.manager.SchedulerManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.core.scheduler.JobContext;
import cafe.jeffrey.server.persistence.api.ProfilerRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.server.core.repository.RemoteWorkspaceRepository;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;
import cafe.jeffrey.server.core.scheduler.job.descriptor.JobDescriptorFactory;
import cafe.jeffrey.server.core.scheduler.job.descriptor.WorkspaceProfilerSettingsSynchronizerJobDescriptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceProfilerSettingsSynchronizerJob extends
        WorkspaceJob<WorkspaceProfilerSettingsSynchronizerJobDescriptor> {

    private final Duration period;
    private final ProfilerRepository profilerRepository;
    private final ServerPlatformRepositories platformRepositories;

    public WorkspaceProfilerSettingsSynchronizerJob(
            Duration period,
            ProfilerRepository profilerRepository,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            ServerPlatformRepositories platformRepositories,
            JobDescriptorFactory jobDescriptorFactory) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.period = period;
        this.profilerRepository = profilerRepository;
        this.platformRepositories = platformRepositories;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, WorkspaceProfilerSettingsSynchronizerJobDescriptor jobDescriptor, JobContext context) {

        RemoteWorkspaceRepository workspaceRepository = workspaceManager.remoteWorkspaceRepository();

        List<ProfilerInfo> profilerInfos = profilerRepository.findWorkspaceSettings(
                workspaceManager.resolveInfo().id());

        ProfilerSettings profilerSettings = resolveProfilerSettings(profilerInfos);
        workspaceRepository.uploadSettings(new RemoteWorkspaceSettings(profilerSettings));
        workspaceRepository.removeLegacySettings(jobDescriptor.maxVersions());
    }

    private ProfilerSettings resolveProfilerSettings(List<ProfilerInfo> profilerInfos) {
        String globalSettings = null;
        String workspaceSettings = null;
        Map<String, String> projectSettings = new HashMap<>();
        for (ProfilerInfo profilerInfo : profilerInfos) {
            if (profilerInfo.isGlobal()) {
                globalSettings = profilerInfo.agentSettings();
            } else if (profilerInfo.isWorkspace()) {
                workspaceSettings = profilerInfo.agentSettings();
            } else {
                platformRepositories.newProjectRepository(profilerInfo.projectId())
                        .find()
                        .ifPresent(info -> projectSettings.put(info.name(), profilerInfo.agentSettings()));
            }
        }

        String defaultSettings = workspaceSettings != null ? workspaceSettings : globalSettings;
        String defaultSettingsLevel = workspaceSettings != null ? "WORKSPACE" : (globalSettings != null ? "GLOBAL" : null);
        return new ProfilerSettings(defaultSettings, defaultSettingsLevel, projectSettings);
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_PROFILER_SETTINGS_SYNCHRONIZER;
    }
}
