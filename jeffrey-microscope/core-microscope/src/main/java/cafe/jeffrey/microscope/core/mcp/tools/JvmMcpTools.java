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
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.FlagsData;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.provider.profile.api.JvmFlagDetail;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.Type;
import tools.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

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

    /**
     * The page in the Jeffrey UI each section is drawn on, so an answer can hand the reader the
     * dashboard it came from. Only the sections that have a page of their own are here; one without an
     * entry simply carries no link rather than a guessed one.
     */
    private static final String THREAD_DUMPS_VIEW = "thread-dumps";
    private static final String FLAGS_VIEW = "flags";

    private static final Map<String, String> SECTION_VIEWS = Map.of(
            AutoAnalysisSection.ID, "auto-analysis",
            GcSection.ID, "garbage-collection",
            SafepointsSection.ID, "vm-operations",
            JitSection.ID, "jit-compilation",
            ThreadsSection.ID, "thread-statistics",
            NativeMemorySection.ID, "native-memory",
            ContainerSection.ID, "container/configuration",
            ConfigurationSection.ID, "overview");

    private static final String NOT_RECORDED = "This profile has no data for the %s section. It is "
            + "built from %s, and this recording carries none of them — the profiler was not "
            + "configured to capture them. Call jvm_sections to see which sections this profile can "
            + "answer.";

    private static final String AUTO_ANALYSIS_NOT_COMPUTED = "Auto Analysis has not been computed for "
            + "this profile yet. Call this tool again with compute true to run it — it reads the whole "
            + "recording through the JMC rule set, which takes a while and is cached afterwards. It can "
            + "also be run from the Auto Analysis page in the Jeffrey UI; call profiles_link for the "
            + "URL. Meanwhile the other jvm_ sections answer the same subsystems directly from the "
            + "parsed events.";

    private static final String AUTO_ANALYSIS_COMPUTE_DISABLED = "Auto Analysis has not been computed "
            + "for this profile, and this Jeffrey runs with jeffrey.microscope.mcp.compute.enabled "
            + "false, so it cannot be computed from here. Run it once from the Auto Analysis page in "
            + "the Jeffrey UI — call profiles_link for the URL — or use the other jvm_ sections, which "
            + "answer the same subsystems directly from the parsed events.";

    private static final String NO_THREAD_DUMPS =
            "This profile carries no thread dumps. They come from jdk.ThreadDump events, which a "
                    + "recording only holds when the profiler was asked for them - jvm_threads answers "
                    + "the per-thread CPU and allocation questions either way.";

    private static final String NO_SUCH_DUMP =
            "This profile has no thread dump at index %d. jvm_threadDumps lists the dumps it does have, "
                    + "each with its index and time offset.";

    private static final String NO_FLAGS =
            "This profile recorded no JVM flag events, so the flags the JVM ran with are unknown. "
                    + "jvm_configuration still reports the collector, heap and compiler settings the "
                    + "JVM applied.";

    private static final List<String> THREAD_DUMP_STEPS = List.of(
            "A deadlock names the threads in the cycle; jvm_threadDump opens one dump in full, with "
                    + "every stack as the JVM printed it.",
            "A thread stuck across consecutive dumps is waiting on something. What it waits on is "
                    + "blocking_monitors; what it was executing is its top frame here.");

    private static final List<String> FLAGS_STEPS = List.of(
            "An origin of 'default' means nobody set the flag; 'ergonomic' means the JVM chose it from "
                    + "the machine it started on, which is why it can differ between environments.",
            "What the collector and compiler actually did with these values is in jvm_gc and jvm_jit.");

    private static final String NO_SUCH_CONFIGURATION_SECTION =
            "This profile has no configuration section named '%s'. Call jvm_configuration without a "
                    + "section to see the sections it does have.";

    private final JvmSections sections;
    private final AutoAnalysisSection autoAnalysisSection;
    private final ConfigurationSection configurationSection;
    private final ProfileManager profileManager;
    private final boolean computeEnabled;

    public JvmMcpTools(ProfileManager profileManager) {
        this(profileManager, true);
    }

    /**
     * @param computeEnabled whether this installation lets a tool build something before answering.
     *                       Only auto analysis is affected: everything else in the family reads events
     *                       that were parsed when the profile was created
     */
    public JvmMcpTools(ProfileManager profileManager, boolean computeEnabled) {
        this.profileManager = profileManager;
        this.computeEnabled = computeEnabled;
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
            + "Cached once computed, and read from that cache here. When nothing has computed it yet, "
            + "pass compute true to run it: that reads the whole recording through the rule set, which "
            + "is slow and unbounded in memory, so it is asked for rather than assumed.")
    public String autoAnalysis(
            @ToolParam(required = false, description = "Run the rule set now if it has not been run "
                    + "before. Off by default because it reads the entire recording through the JMC "
                    + "toolkit, which on a large one takes a while and holds a lot of heap. Ignored "
                    + "when the analysis is already computed, which is then simply returned")
            Boolean compute) {

        if (!autoAnalysisSection.isComputed()) {
            if (!Boolean.TRUE.equals(compute)) {
                return AUTO_ANALYSIS_NOT_COMPUTED;
            }
            if (!computeEnabled) {
                return AUTO_ANALYSIS_COMPUTE_DISABLED;
            }
            profileManager.autoAnalysisManager().generate();
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
            return McpToolOutput.json(result(declared, configurationSection.sectionNames()));
        }

        JsonNode content = configurationSection.section(section);
        if (content == null) {
            return McpToolOutput.error(NO_SUCH_CONFIGURATION_SECTION.formatted(section));
        }
        return McpToolOutput.json(result(declared, content));
    }

    @Tool(description = "The thread dumps this recording captured, analysed together: how many dumps "
            + "and at what offsets, peak thread count, the deadlocks found, the monitors threads were "
            + "queueing on, the threads stuck across consecutive dumps, and the frames that appear "
            + "most often. This is the tool for 'it stopped responding' - a deadlock or a thread pool "
            + "all blocked on one lock is visible here and in nothing else.")
    public String threadDumps() {
        ThreadDumpAnalysis analysis = profileManager.threadManager().threadDumpAnalysis();
        if (analysis == null || analysis.header().dumpCount() == 0) {
            return NO_THREAD_DUMPS;
        }

        return McpToolOutput.json(new ThreadDumps(
                analysis.header(),
                analysis.dumps(),
                analysis.deadlocks(),
                analysis.lockContention(),
                analysis.stuckThreads(),
                analysis.topFrames(),
                THREAD_DUMP_STEPS,
                UiLinks.view(profileManager.info().id(), THREAD_DUMPS_VIEW)));
    }

    @Tool(description = "One thread dump in full: every thread with its state and stack, and the "
            + "deadlocks the JVM detected in it. Use it after jvm_threadDumps has named the dump worth "
            + "reading - the analysis says which index holds the deadlock or the stuck threads.")
    public String threadDump(
            @ToolParam(required = true, description = "Index of the dump, as listed by jvm_threadDumps")
            Integer index) {

        if (index == null) {
            throw new IllegalArgumentException("index is required; jvm_threadDumps lists the dumps");
        }

        ParsedDump dump = profileManager.threadManager().threadDump(index);
        if (dump == null) {
            return NO_SUCH_DUMP.formatted(index);
        }

        return McpToolOutput.json(new ThreadDumpDetail(
                index,
                dump.timeOffsetMillis(),
                dump.threads(),
                dump.deadlocks(),
                THREAD_DUMP_STEPS,
                UiLinks.view(profileManager.info().id(), THREAD_DUMPS_VIEW)));
    }

    @Tool(description = "The JVM flags this run actually used, grouped by where each value came from - "
            + "a default, the command line, or the JVM's own ergonomics. Read this before proposing "
            + "any flag: it is the only place that distinguishes a flag someone set from one the JVM "
            + "chose, and a deployment manifest is not evidence of either. jvm_configuration reports "
            + "the resulting collector and heap settings; this reports the switches.")
    public String flags() {
        FlagsData flags = profileManager.flagsManager().getAllFlags();
        if (flags == null || flags.totalFlags() == 0) {
            return NO_FLAGS;
        }

        return McpToolOutput.json(new Flags(
                flags.totalFlags(),
                flags.changedFlags(),
                flags.flagsByOrigin(),
                FLAGS_STEPS,
                UiLinks.view(profileManager.info().id(), FLAGS_VIEW)));
    }

    private String render(String id) {
        JvmSection section = sections.get(id);
        if (!sections.isAvailable(section)) {
            return notRecorded(section);
        }
        return McpToolOutput.json(result(section, section.render()));
    }

    /**
     * A dashboard with the routing that belongs beside it.
     * <p>
     * The envelope is the family's contract rather than each dashboard's, so a section describes what
     * it cannot answer once and every result carries it — at the moment the figures are read, not in
     * a tool description from many turns earlier.
     */
    private SectionResult result(JvmSection section, Object dashboard) {
        return new SectionResult(
                section.id(), section.title(), section.nextSteps(), dashboard, viewLink(section));
    }

    /**
     * The dashboard's own page, for the reader rather than for the model - a URL carries nothing that
     * can be analysed, which is why it travels with an answer instead of behind a tool of its own.
     */
    private String viewLink(JvmSection section) {
        String view = SECTION_VIEWS.get(section.id());
        return view == null ? null : UiLinks.view(profileManager.info().id(), view);
    }

    /**
     * @param nextSteps what this dashboard cannot answer and which tool answers it — routing, never a
     *                  verdict on whether the figures below are good or bad
     */
    private record ThreadDumps(
            ThreadDumpAnalysis.Header header,
            List<ThreadDumpAnalysis.DumpDescriptor> dumps,
            List<ThreadDumpAnalysis.DeadlockEntry> deadlocks,
            List<ThreadDumpAnalysis.LockContention> lockContention,
            List<ThreadDumpAnalysis.StuckThread> stuckThreads,
            List<ThreadDumpAnalysis.FrameStat> topFrames,
            List<String> nextSteps,
            String uiLink) {
    }

    private record ThreadDumpDetail(
            int index,
            long timeOffsetMillis,
            List<ParsedDump.ParsedThread> threads,
            List<ParsedDump.Deadlock> deadlocks,
            List<String> nextSteps,
            String uiLink) {
    }

    private record Flags(
            int totalFlags,
            int changedFlags,
            Map<String, List<JvmFlagDetail>> flagsByOrigin,
            List<String> nextSteps,
            String uiLink) {
    }

    private record SectionResult(
            String section,
            String title,
            List<String> nextSteps,
            Object dashboard,
            String uiLink) {
    }

    private static String notRecorded(JvmSection section) {
        List<String> eventTypes = section.eventTypes().stream()
                .map(Type::code)
                .sorted()
                .toList();
        return NOT_RECORDED.formatted(section.title(), String.join(", ", eventTypes));
    }
}
