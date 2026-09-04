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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ContainerSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JitSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.NativeMemorySection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SafepointsSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ThreadsSection;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.Type;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * The machine underneath the application: garbage collection, safepoints, JIT compilation, threads,
 * native memory, the container, and what the JVM was started with.
 * <p>
 * Every one of these is answerable from the profile database with SQL, and that is exactly the problem
 * this family solves. Answering "how much of the run went to GC pauses" by hand is six round trips of
 * inventing queries, and several of those queries are ones a reader reliably gets wrong — pause time
 * is {@code sumOfPauses} rather than an event's duration, {@code jdk.GCHeapSummary} is two rows per
 * collection, {@code jdk.SafepointLatency} fires once per thread per safepoint. Each tool here renders
 * the manager behind the matching Jeffrey UI page, so the numbers come from builders that have been
 * making those distinctions correctly for far longer, and cost one call.
 * <p>
 * Start from {@link #sections()}: a recording only carries the events the profiler was configured to
 * capture, and a dashboard built from events that were never recorded is a page of zeroes that reads
 * like a finding. A section asked for anyway is refused with the events it needed, rather than
 * answered with nothing.
 */
public class JvmMcpTools {

    private static final String NOT_RECORDED = "This profile has no data for the %s section. It is "
            + "built from %s, and this recording carries none of them — the profiler was not "
            + "configured to capture them. Call jvm_sections to see which sections this profile can "
            + "answer.";

    private static final String AUTO_ANALYSIS_NOT_COMPUTED = "Auto Analysis has not been computed for "
            + "this profile yet. It runs the JMC rule set over the whole recording, which is done "
            + "once from the Auto Analysis page in the Jeffrey UI and cached for every later read — "
            + "call profiles_link for the URL. Meanwhile the other jvm_ sections answer the same "
            + "subsystems directly from the parsed events.";

    private static final String NO_SUCH_CONFIGURATION_SECTION =
            "This profile has no configuration section named '%s'. Call jvm_configuration without a "
                    + "section to see the sections it does have.";

    private final JvmSections sections;
    private final AutoAnalysisSection autoAnalysisSection;
    private final ConfigurationSection configurationSection;

    public JvmMcpTools(ProfileManager profileManager) {
        // Two of the sections answer more than "render me": auto analysis reports whether it has been
        // computed at all, and configuration is asked for one tab at a time. They are built here and
        // handed to the registry so there is one instance of each, not one per caller.
        this.autoAnalysisSection = new AutoAnalysisSection(profileManager);
        this.configurationSection = new ConfigurationSection(profileManager);

        this.sections = new JvmSections(profileManager, List.of(
                autoAnalysisSection,
                new GcSection(profileManager),
                new SafepointsSection(profileManager),
                new JitSection(profileManager),
                new ThreadsSection(profileManager),
                new NativeMemorySection(profileManager),
                new ContainerSection(profileManager),
                configurationSection));
    }

    @Tool(description = "Which machine-level dashboards this profile can answer — garbage collection, "
            + "safepoints, JIT compilation, threads, native memory, the container, the JVM's "
            + "configuration and Jeffrey's own auto analysis — each with the event types it is built "
            + "from and whether the recording carries them. Call this before the other jvm_ tools: a "
            + "recording only holds what the profiler was told to capture.")
    public String sections() {
        return McpToolOutput.json(sections.availability());
    }

    @Tool(description = "Jeffrey's Auto Analysis: the JMC rule set run over the whole recording, as "
            + "findings with a severity, an explanation and a suggested fix. The cheapest first "
            + "question about any profile — each finding names a subsystem worth following up in. "
            + "Computed once from the Auto Analysis page in the Jeffrey UI and cached; this tool "
            + "reads that cache and says so when it is empty.")
    public String autoAnalysis() {
        if (!autoAnalysisSection.isComputed()) {
            return AUTO_ANALYSIS_NOT_COMPUTED;
        }
        return render(AutoAnalysisSection.ID);
    }

    @Tool(description = "The garbage collection dashboard: the stop-the-world budget this recording "
            + "paid and how it was distributed, collections split by generation, what caused them, "
            + "how much was freed, and the longest individual collections. Pause figures are "
            + "sumOfPauses and longestPause, never the event duration, which for ZGC, Shenandoah and "
            + "G1's concurrent cycles covers phases the application ran straight through. No GC event "
            + "names the code producing the garbage — for that, export an allocation flamegraph.")
    public String gc() {
        return render(GcSection.ID);
    }

    @Tool(description = "The safepoints and VM operations dashboard: the pauses that are not garbage "
            + "collection. Every VM operation stops the application the same way a collection does, "
            + "and a thread slow to reach the safepoint holds every other thread there. Answers 'GC "
            + "looks fine and we still have pauses', and names the threads that keep everyone else "
            + "waiting with the state they were in — in Java means a loop the JIT stripped the "
            + "safepoint poll out of, in native means a call the JVM cannot interrupt.")
    public String safepoints() {
        return render(SafepointsSection.ID);
    }

    @Tool(description = "The JIT compilation dashboard: compiler totals, the slowest compilations, "
            + "code cache occupancy, and deoptimisations aggregated by method and reason. A method "
            + "deoptimised repeatedly ran interpreted for part of the recording; a code cache that "
            + "ran full stopped compilation altogether. An empty compilation list means nothing "
            + "compiled slowly enough to cross the recording's threshold, not that nothing compiled — "
            + "the statistics are there either way.")
    public String jit() {
        return render(JitSection.ID);
    }

    @Tool(description = "The threads dashboard: how many threads there were and at peak, how often "
            + "they slept, parked and blocked on monitors, which threads burned the most user and "
            + "system CPU, which allocated the most bytes, and — for a Loom application — how often a "
            + "virtual thread pinned its carrier, for how long and why. A flamegraph aggregates "
            + "across threads; this is the per-thread attribution it hides.")
    public String threads() {
        return render(ThreadsSection.ID);
    }

    @Tool(description = "The native memory dashboard: resident set size and its growth, direct byte "
            + "buffers, loaded native libraries, and the Native Memory Tracking categories when the "
            + "JVM was started with NMT enabled. This is the half of a memory problem neither a "
            + "flamegraph nor a heap dump can see — a process killed for memory while the Java heap "
            + "looked healthy.")
    public String nativeMemory() {
        return render(NativeMemorySection.ID);
    }

    @Tool(description = "The container dashboard: the cgroup limits the JVM read at start-up (CPU "
            + "quota and period, effective processor count, memory limits) and whether the scheduler "
            + "throttled the process, with Jeffrey's verdict and the counters behind it. Answers "
            + "'slow in the cluster, fine on my laptop' — CFS throttling parks every thread once the "
            + "quota is spent, which a CPU flamegraph cannot show.")
    public String container() {
        return render(ContainerSection.ID);
    }

    @Tool(description = "What the JVM was actually started with, in the labelled sections the Jeffrey "
            + "UI shows as tabs: application and JVM information, CPU and operating system, the "
            + "collector, heap, survivor, TLAB and young-generation settings, the compiler, the "
            + "container and the virtualisation. Called without a section it lists the section names "
            + "this profile has. Read it before proposing any flag: a tuning recommendation is only "
            + "worth making against the values the JVM really ran with.")
    public String configuration(
            @ToolParam(required = false, description = "One section name as returned by this tool "
                    + "with no argument, e.g. 'GC Heap Configuration'. Omit for the list of sections.")
            String section) {

        JvmSection declared = sections.get(ConfigurationSection.ID);
        if (!sections.isAvailable(declared)) {
            return notRecorded(declared);
        }
        if (section == null || section.isBlank()) {
            return McpToolOutput.json(configurationSection.sectionNames());
        }

        JsonNode content = configurationSection.section(section);
        if (content == null) {
            return McpToolOutput.error(NO_SUCH_CONFIGURATION_SECTION.formatted(section));
        }
        return McpToolOutput.json(content);
    }

    private String render(String id) {
        JvmSection section = sections.get(id);
        if (!sections.isAvailable(section)) {
            return notRecorded(section);
        }
        return McpToolOutput.json(section.render());
    }

    private static String notRecorded(JvmSection section) {
        List<String> eventTypes = section.eventTypes().stream()
                .map(Type::code)
                .sorted()
                .toList();
        return NOT_RECORDED.formatted(section.title(), String.join(", ", eventTypes));
    }
}
