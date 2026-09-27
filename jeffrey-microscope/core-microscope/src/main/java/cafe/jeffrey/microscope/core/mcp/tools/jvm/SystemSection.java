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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingSpan;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The machine the JVM was running on, and who else was on it.
 * <p>
 * This section exists to answer one question the rest of the profile cannot: is it my JVM or the box?
 * The recording measures machine CPU and JVM CPU separately, and the gap between them is everything
 * else running there. A profile whose own CPU is modest while the machine is saturated describes an
 * application being starved, not one being slow, and every flamegraph taken from it will mislead a
 * reader who has not seen this number.
 * <p>
 * CPU values arrive as basis points and are reported here as percentages, because a reader comparing
 * them against a container limit is thinking in percent.
 */
public record SystemSection(ProfileManager profileManager) implements JvmSection<SystemSection.SystemDashboard> {

    public static final String ID = "system";

    private static final String TITLE = "System & Host";

    private static final int PROCESSES_LIMIT = 20;
    private static final int LAUNCHED_LIMIT = 15;
    private static final double BASIS_POINTS_IN_PERCENT = 100d;

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.CPU_LOAD,
            Type.NETWORK_UTILIZATION,
            Type.THREAD_CONTEXT_SWITCH_RATE,
            Type.SYSTEM_PROCESS,
            Type.PROCESS_START,
            Type.SWAP_SPACE);

    private static final String NEIGHBOUR_GUIDANCE =
            "otherCpuPercent is the machine minus this JVM - a noisy neighbour, not this application. "
                    + "When it is large, the flamegraphs describe a process that was being starved.";
    private static final String CGROUP_WHY =
            "reports the cgroup limits and whether the scheduler throttled the process, the more common "
                    + "cap in a container";
    private static final String CONTEXT_SWITCHES_WHY =
            "names the locks threads queue on, which is what a high context-switch rate with modest CPU "
                    + "usually is";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    @Override
    public MicroscopeView view() {
        return MicroscopeView.SYSTEM;
    }

    @Override
    public void followUp(NextSteps.Builder next, SystemDashboard dashboard) {
        String profileId = profileManager.info().id();
        next.next(SectionCalls.on(SectionCalls.JVM_CONTAINER, profileId).why(CGROUP_WHY))
                .next(SectionCalls.on(SectionCalls.BLOCKING_MONITORS, profileId).why(CONTEXT_SWITCHES_WHY))
                .guidance(NEIGHBOUR_GUIDANCE);
    }

    @Override
    public SystemDashboard render() {
        SystemOverview overview = profileManager.systemResourcesManager().overview();
        return new SystemDashboard(
                percent(overview.avgMachineCpuBp()),
                percent(overview.maxMachineCpuBp()),
                percent(overview.avgJvmCpuBp()),
                percent(overview.avgOtherCpuBp()),
                overview.maxContextSwitchRateHz(),
                overview.processCount(),
                overview.networkInterfaceCount(),
                profileManager.systemResourcesManager().networkInterfaces(),
                processes(),
                launchedProcesses());
    }

    private List<Process> processes() {
        return profileManager.systemResourcesManager().processes().stream()
                .limit(PROCESSES_LIMIT)
                .map(process -> new Process(process.pid(), process.commandLine()))
                .toList();
    }

    private List<LaunchedProcess> launchedProcesses() {
        Optional<RecordingSpan> span = RecordingSpan.of(profileManager.info());
        return profileManager.systemResourcesManager().launchedProcesses().stream()
                .limit(LAUNCHED_LIMIT)
                .map(process -> new LaunchedProcess(
                        span.map(recording -> recording.epochAt(process.timeOffsetMillis())).orElse(null),
                        process.pid(),
                        process.command(),
                        process.thread()))
                .toList();
    }

    private static double percent(long basisPoints) {
        return basisPoints / BASIS_POINTS_IN_PERCENT;
    }

    /**
     * @param otherCpuPercent the machine's CPU minus this JVM's — everything else on the box
     */
    public record SystemDashboard(
            double avgMachineCpuPercent,
            double maxMachineCpuPercent,
            double avgJvmCpuPercent,
            double otherCpuPercent,
            long maxContextSwitchRateHz,
            int processCount,
            int networkInterfaceCount,
            List<String> networkInterfaces,
            @McpDescription("The first " + PROCESSES_LIMIT + " processes on the machine, out of processCount")
            List<Process> processes,
            @McpDescription("The first " + LAUNCHED_LIMIT + " processes this JVM started")
            List<LaunchedProcess> launchedProcesses) {
    }

    public record Process(
            @McpNullable
            String pid,
            @McpNullable
            String commandLine) {
    }

    /**
     * A process this JVM started. Rare, and worth seeing when it happens: forking from a server is
     * expensive and often unintended.
     *
     * @param startedAtEpochMs when it was started, as UTC epoch milliseconds; null when the profile
     *                         carries no recording span to place it on
     */
    public record LaunchedProcess(
            @McpNullable
            Long startedAtEpochMs,
            long pid,
            @McpNullable
            String command,
            @McpNullable
            String thread) {
    }

    /**
     * What {@code jvm_system} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            SystemDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, SystemDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
