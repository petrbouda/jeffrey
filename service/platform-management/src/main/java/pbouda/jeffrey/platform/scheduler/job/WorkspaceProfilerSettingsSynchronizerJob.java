package pbouda.jeffrey.platform.scheduler.job;

import pbouda.jeffrey.shared.model.ProfilerInfo;
import pbouda.jeffrey.shared.model.job.JobType;
import pbouda.jeffrey.shared.model.workspace.WorkspaceType;
import pbouda.jeffrey.platform.manager.SchedulerManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.platform.repository.RemoteWorkspaceRepository;
import pbouda.jeffrey.shared.model.repository.ProfilerSettings;
import pbouda.jeffrey.shared.model.repository.RemoteWorkspaceSettings;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.platform.scheduler.job.descriptor.WorkspaceProfilerSettingsSynchronizerJobDescriptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceProfilerSettingsSynchronizerJob extends
        WorkspaceJob<WorkspaceProfilerSettingsSynchronizerJobDescriptor> {

    private final Duration period;
    private final ProfilerRepository profilerRepository;
    private final Repositories repositories;

    public WorkspaceProfilerSettingsSynchronizerJob(
            Duration period,
            ProfilerRepository profilerRepository,
            WorkspacesManager workspacesManager,
            SchedulerManager schedulerManager,
            Repositories repositories,
            JobDescriptorFactory jobDescriptorFactory) {

        super(workspacesManager, schedulerManager, jobDescriptorFactory);
        this.period = period;
        this.profilerRepository = profilerRepository;
        this.repositories = repositories;
    }

    @Override
    protected void executeOnWorkspace(
            WorkspaceManager workspaceManager, WorkspaceProfilerSettingsSynchronizerJobDescriptor jobDescriptor, JobContext context) {

        if (workspaceManager.type() != WorkspaceType.LIVE) {
            return;
        }

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
                repositories.newProjectRepository(profilerInfo.projectId())
                        .find()
                        .ifPresent(info -> projectSettings.put(info.name(), profilerInfo.agentSettings()));
            }
        }

        return new ProfilerSettings(workspaceSettings != null ? workspaceSettings : globalSettings, projectSettings);
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
