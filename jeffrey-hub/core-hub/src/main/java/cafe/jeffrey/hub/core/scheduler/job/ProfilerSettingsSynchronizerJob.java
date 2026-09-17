/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.model.ProfilerInfo;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.persistence.api.ProfilerRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.core.workspace.settings.WorkspaceSettingsPublisher;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfilerSettingsSynchronizerJob extends WorkspaceJob {

    private static final Logger LOG = LoggerFactory.getLogger(ProfilerSettingsSynchronizerJob.class);

    /** How many older published settings files a workspace keeps beside the current one. */
    private static final String PARAM_MAX_VERSIONS = "max-versions";

    private final Duration period;
    private final int maxVersions;
    private final ProfilerRepository profilerRepository;
    private final HubPlatformRepositories platformRepositories;

    public ProfilerSettingsSynchronizerJob(
            WorkspacesManager workspacesManager,
            JobConfig config,
            ProfilerRepository profilerRepository,
            HubPlatformRepositories platformRepositories) {
        super(workspacesManager);
        this.period = config.period();
        this.maxVersions = config.intParam(PARAM_MAX_VERSIONS);
        this.profilerRepository = profilerRepository;
        this.platformRepositories = platformRepositories;
    }

    @Override
    protected void executeOnWorkspace(WorkspaceManager workspaceManager) {
        WorkspaceSettingsPublisher settingsPublisher = workspaceManager.settingsPublisher();

        List<ProfilerInfo> profilerInfos = profilerRepository.findWorkspaceSettings(
                workspaceManager.resolveInfo().id());

        ProfilerSettings profilerSettings = resolveProfilerSettings(profilerInfos);
        settingsPublisher.uploadSettings(new RemoteWorkspaceSettings(profilerSettings));
        settingsPublisher.removeLegacySettings(maxVersions);
    }

    private ProfilerSettings resolveProfilerSettings(List<ProfilerInfo> profilerInfos) {
        String globalSettings = null;
        String workspaceSettings = null;
        Map<String, String> projectSettings = new HashMap<>();
        Map<String, String> projectSettingsById = new HashMap<>();
        for (ProfilerInfo profilerInfo : profilerInfos) {
            if (profilerInfo.isGlobal()) {
                globalSettings = profilerInfo.agentSettings();
            } else if (profilerInfo.isWorkspace()) {
                workspaceSettings = profilerInfo.agentSettings();
            } else {
                platformRepositories.newProjectRepository(profilerInfo.projectId())
                        .find()
                        .ifPresent(info -> {
                            // The bundle is published under two keys: the origin project id
                            // (authoritative — the id the provisioner knows from .project-info.json)
                            // and the project name (fallback for older provisioners). Two same-named
                            // projects still overwrite each other in the name map, so make the
                            // collision visible; the id-keyed lookup is unaffected by it.
                            if (info.originId() != null) {
                                projectSettingsById.put(info.originId(), profilerInfo.agentSettings());
                            }
                            String previous = projectSettings.put(info.name(), profilerInfo.agentSettings());
                            if (previous != null && !previous.equals(profilerInfo.agentSettings())) {
                                LOG.warn("Multiple projects share the same name, their profiler settings " +
                                                "overwrite each other in the uploaded bundle (id-keyed lookup " +
                                                "is authoritative for new provisioners): project_name={} project_id={}",
                                        info.name(), profilerInfo.projectId());
                            }
                        });
            }
        }

        String defaultSettings = workspaceSettings != null ? workspaceSettings : globalSettings;
        String defaultSettingsLevel = workspaceSettings != null
                ? ProfilerSettings.WORKSPACE_LEVEL
                : (globalSettings != null ? ProfilerSettings.GLOBAL_LEVEL : null);
        return new ProfilerSettings(defaultSettings, defaultSettingsLevel, projectSettings, projectSettingsById);
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROFILER_SETTINGS_SYNCHRONIZER;
    }
}
