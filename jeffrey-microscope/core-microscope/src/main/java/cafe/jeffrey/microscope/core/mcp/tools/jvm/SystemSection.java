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
package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
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
public record SystemSection(ProfileManager profileManager) implements JvmSection {

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

    private static final List<String> NEXT_STEPS = List.of(
            "otherCpuPercent is the machine minus this JVM — a noisy neighbour, not this application. "
                    + "When it is large, the flamegraphs describe a process that was being starved.",
            "A cgroup limit below what the machine offers is the more common cap in a container: "
                    + "jvm_container reports the limits and whether the scheduler throttled the process.",
            "A high context-switch rate with modest CPU usually means threads contending rather than "
                    + "working — blocking_monitors names the locks they are queuing on.");

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
    public List<String> nextSteps() {
        return NEXT_STEPS;
    }

    @Override
    public Object render() {
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
        return profileManager.systemResourcesManager().launchedProcesses().stream()
                .limit(LAUNCHED_LIMIT)
                .map(process -> new LaunchedProcess(
                        process.timeOffsetMillis(), process.pid(), process.command(), process.thread()))
                .toList();
    }

    private static double percent(long basisPoints) {
        return basisPoints / BASIS_POINTS_IN_PERCENT;
    }

    /**
     * @param otherCpuPercent the machine's CPU minus this JVM's — everything else on the box
     */
    private record SystemDashboard(
            double avgMachineCpuPercent,
            double maxMachineCpuPercent,
            double avgJvmCpuPercent,
            double otherCpuPercent,
            long maxContextSwitchRateHz,
            int processCount,
            int networkInterfaceCount,
            List<String> networkInterfaces,
            List<Process> processes,
            List<LaunchedProcess> launchedProcesses) {
    }

    private record Process(String pid, String commandLine) {
    }

    /**
     * A process this JVM started. Rare, and worth seeing when it happens: forking from a server is
     * expensive and often unintended.
     */
    private record LaunchedProcess(long timeOffsetMillis, long pid, String command, String thread) {
    }
}
