/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview.ProjectStorage;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.ManuallyTriggerable;
import cafe.jeffrey.hub.model.job.JobType;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.BytesUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Every retention rule of every project in one pass, and the storage overview from what is left.
 *
 * <p>Each project's sessions are listed once, with their files — a stat per file over the volume,
 * which is the expensive part — and the rules run over that one listing in order: the session age
 * window and per-instance cap, the trim of live sessions, the disk quota, then stale instances.
 * What remains is measured into {@link StorageOverviewCache}, so the dashboard needs no walk of
 * its own.</p>
 */
public class ProjectRetentionJob extends FanOutJob implements ManuallyTriggerable {

    private static final String PARAM_SESSION_RETENTION = "session-retention";
    private static final String PARAM_MAX_SESSIONS = "max-sessions";
    private static final String PARAM_ACTIVE_SESSION_FILE_RETENTION = "active-session-file-retention";
    private static final String PARAM_MAX_SIZE = "max-size";
    private static final String PARAM_INSTANCE_RETENTION = "instance-retention";

    /**
     * What one pass over every project did, for the operator who ran it by hand.
     */
    record Summary(int projects, int sessionsDeleted, int filesDeleted, int instancesDeleted, long usedBytes) {

        static Summary of(List<ProjectRetentionPass.Outcome> outcomes, List<ProjectStorage> measured) {
            return new Summary(
                    measured.size(),
                    outcomes.stream().mapToInt(ProjectRetentionPass.Outcome::sessionsDeleted).sum(),
                    outcomes.stream().mapToInt(ProjectRetentionPass.Outcome::filesDeleted).sum(),
                    outcomes.stream().mapToInt(ProjectRetentionPass.Outcome::instancesDeleted).sum(),
                    measured.stream().mapToLong(ProjectStorage::totalSizeBytes).sum());
        }

        String describe() {
            return "Deleted " + count(sessionsDeleted, "session") + ", " + count(filesDeleted, "file")
                    + " and " + count(instancesDeleted, "instance") + "; "
                    + BytesUtils.format(usedBytes) + " across " + count(projects, "project");
        }

        private static String count(int value, String noun) {
            return value + " " + noun + (value == 1 ? "" : "s");
        }
    }

    private final List<RetentionRule> rules;
    private final StorageOverviewCache storageOverview;
    private final Clock clock;
    private final Duration period;

    public ProjectRetentionJob(
            WorkspacesManager workspacesManager,
            JobConfig config,
            StorageOverviewCache storageOverview,
            Clock clock) {

        super(workspacesManager);
        this.rules = List.of(
                new SessionRetentionRule(
                        config.durationParam(PARAM_SESSION_RETENTION), config.intParam(PARAM_MAX_SESSIONS)),
                new LiveSessionTrimRule(config.durationParam(PARAM_ACTIVE_SESSION_FILE_RETENTION)),
                new StorageQuotaRule(config.bytesParam(PARAM_MAX_SIZE)),
                new StaleInstanceRule(config.durationParam(PARAM_INSTANCE_RETENTION)));
        this.storageOverview = storageOverview;
        this.clock = clock;
        this.period = config.period();
    }

    @Override
    public void execute() {
        runPass();
    }

    /** Runs a full pass now, deleting what is due, and refreshes the storage overview. */
    @Override
    public String runManually() {
        return runPass().describe();
    }

    private Summary runPass() {
        List<ProjectRetentionPass.Outcome> outcomes = new ArrayList<>();
        List<ProjectStorage> measured = new ArrayList<>();
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            WorkspaceInfo workspace = workspaceManager.info();
            for (ProjectManager project : workspaceManager.projectsManager().findAll()) {
                visit("workspace_id=" + workspace.id() + " project_id=" + project.info().id(), () -> {
                    ProjectRetentionPass.Outcome outcome = retain(project);
                    outcomes.add(outcome);
                    measured.add(ProjectStorage.measure(workspace, project.info(), outcome.remaining()));
                });
            }
        }
        storageOverview.publish(measured);
        return Summary.of(outcomes, measured);
    }

    /** One project: every rule, in order, over a single listing of its sessions. */
    ProjectRetentionPass.Outcome retain(ProjectManager project) {
        ProjectRetentionPass pass = ProjectRetentionPass.begin(project, clock.instant());
        for (RetentionRule rule : rules) {
            rule.apply(pass);
        }
        return pass.outcome();
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.PROJECT_RETENTION;
    }
}
