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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisSection.AutoAnalysisDashboard;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ClassLoadingSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationSection.ConfigurationDashboard;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationTab;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ContainerSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ExceptionsSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcDetailPage;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcDetailSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JitSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.NativeMemorySection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SafepointsSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SectionHeader;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SectionStatus;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SecuritySection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SystemSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ThreadsSection;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.FlagsData;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadState;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.provider.profile.api.FlagValueChange;
import cafe.jeffrey.provider.profile.api.JvmFlagDetail;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;

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
 * Every answer is a record: the section's typed dashboard inside the envelope every section shares
 * ({@link SectionHeader}), with a {@code status} instead of a sentence when there is nothing to show,
 * the calls to make next and the Microscope page the dashboard is drawn on. A recording only carries
 * the events the profiler was configured to capture, and a dashboard built from events that were
 * never recorded is a page of zeroes that reads like a finding, so such a section answers
 * {@link SectionStatus#NOT_RECORDED} with the events it needed.
 */
public class JvmMcpTools {

    private static final MicroscopeView SECTIONS_VIEW = MicroscopeView.EVENT_TYPES;
    private static final MicroscopeView THREAD_DUMPS_VIEW = MicroscopeView.THREAD_DUMPS;
    private static final MicroscopeView FLAGS_VIEW = MicroscopeView.FLAGS;

    private static final String PROFILE_ID = "profileId";
    private static final String COMPUTE = "compute";
    private static final String OPERATION_ID = "operationId";
    private static final String INDEX = "index";
    private static final String STATE = "state";
    private static final String LIMIT = "limit";
    private static final String EVENT_TYPE_SEPARATOR = ", ";

    private static final String SECTIONS_TOOL = "jvm_sections";
    private static final String AUTO_ANALYSIS_TOOL = "jvm_autoAnalysis";
    private static final String RETRY_WHY = "runs the rule set again, as a new attempt";
    private static final String CONFIGURATION_TOOL = "jvm_configuration";
    private static final String THREADS_TOOL = "jvm_threads";
    private static final String THREAD_DUMPS_TOOL = "jvm_threadDumps";
    private static final String THREAD_DUMP_TOOL = "jvm_threadDump";
    private static final String GC_TOOL = "jvm_gc";
    private static final String JIT_TOOL = "jvm_jit";
    private static final String BLOCKING_MONITORS_TOOL = "blocking_monitors";
    private static final String OPERATIONS_STATUS_TOOL = "operations_status";

    private static final String NOT_RECORDED = "This profile has no data for the %s section. It is built "
            + "from %s, and this recording carries none of them: the profiler was not configured to "
            + "capture them.";
    private static final String NOT_RECORDED_GUIDANCE =
            "Only a new recording with one of %s enabled fills this section.";
    private static final String SECTIONS_WHY = "lists which sections this profile can answer";
    private static final String START_WHY =
            "the cheapest broad look at the profile, each finding naming the dashboard that carries its figures";
    private static final String SECTIONS_GUIDANCE = "Each available section is rendered by the tool its row "
            + "names in sections[].tool, which takes only profileId.";

    private static final String NOT_COMPUTED = "Auto Analysis has not been computed for this profile yet. "
            + "It is normally computed when a recording is imported, so this means one of: the profile "
            + "predates that, the warm-up failed, or it is still running. Running it reads the whole "
            + "recording through the JMC rule set, which takes a while and is cached afterwards.";
    private static final String CANNOT_COMPUTE = "Auto Analysis has not been computed and cannot be: no "
            + "JFR recording file is available for this profile. It was removed, or the profile was not "
            + "imported from a JFR recording.";
    private static final String COMPUTING = "The rules are still running past the wait. The operation "
            + "carries their findings in its result once it completes.";
    private static final String COMPUTE_WHY = "runs the rule set now; slow, it reads the whole recording, "
            + "and it may hand back an operation to follow";
    private static final String OTHER_SECTIONS_WHY =
            "lists the dashboards that answer the same subsystems directly from the parsed events";
    private static final String POLL_WHY = "reports the run's progress, and its findings once it completes";

    /** How many threads one dump answers with unless asked for fewer or more. */
    private static final int DEFAULT_DUMP_THREADS = 50;

    /** The most threads one dump answers with, however many it holds. */
    private static final int MAX_DUMP_THREADS = 500;

    private static final String NO_THREAD_DUMPS = "This profile carries no thread dumps. They come from "
            + "jdk.ThreadDump events, which a recording only holds when the profiler was asked for them.";
    private static final String NO_THREAD_DUMPS_GUIDANCE =
            "Only a new recording with jdk.ThreadDump enabled carries thread dumps.";
    private static final String THREADS_WHY =
            "answers the per-thread CPU and allocation questions from the thread statistics instead";
    private static final String NO_SUCH_DUMP = "This profile has no thread dump at index %d.";
    private static final String THREAD_DUMPS_WHY = "lists the dumps this profile has, each with its index";
    private static final String DEADLOCK_WHY = "opens the dump that holds the deadlock, narrowed to the "
            + "threads queueing on its locks";
    private static final String FIRST_DUMP_WHY = "opens a dump with every stack as the JVM printed it";
    private static final String MONITORS_WHY = "names the locks the blocked threads are waiting on";
    private static final String BLOCKED_WHY = "narrows the dump to the threads queueing on a lock";
    private static final String MORE_THREADS_WHY = "shows more of the matching threads, up to the maximum";
    private static final String DUMPS_TABLE = "dumps";
    private static final String DEADLOCKS_TABLE = "deadlocks";
    private static final String LOCK_CONTENTION_TABLE = "lockContention";
    private static final String STUCK_THREADS_TABLE = "stuckThreads";
    private static final String TOP_FRAMES_TABLE = "topFrames";
    /** A thread stuck longer, and then across more dumps, is the likelier cause of a hang. */
    private static final Comparator<StuckThread> LONGEST_STUCK_FIRST = Comparator
            .comparingLong(StuckThread::stuckForMs)
            .thenComparingInt(StuckThread::consecutiveDumps)
            .reversed();

    private static final String STUCK_GUIDANCE = "A thread stuck across consecutive dumps is waiting on "
            + "something; its top frame is what it was executing.";

    private static final String NO_FLAGS = "This profile recorded no JVM flag events, so the flags the JVM "
            + "ran with are unknown.";
    private static final String CONFIGURATION_WHY =
            "reports the collector, heap and compiler settings the JVM applied, which do not need flag events";
    private static final String ORIGIN_GUIDANCE = "An origin of 'Default' means nobody set the flag; "
            + "'Ergonomic' means the JVM chose it from the machine it started on, which is why it can "
            + "differ between environments.";
    private static final String FLAGS_GC_WHY = "shows what the collector actually did with these values";
    private static final String FLAGS_JIT_WHY = "shows what the compiler actually did with these values";

    private static final String NO_SUCH_CONFIGURATION_SECTION = "This profile has no %s configuration "
            + "section: the recording did not carry that event.";
    private static final String CONFIGURATION_LIST_WHY = "lists the configuration sections this profile has";

    private final JvmSections sections;
    private final AutoAnalysisSection autoAnalysisSection;
    private final GcSection gcSection;
    private final GcDetailSection gcDetailSection;
    private final SafepointsSection safepointsSection;
    private final JitSection jitSection;
    private final ThreadsSection threadsSection;
    private final NativeMemorySection nativeMemorySection;
    private final ClassLoadingSection classLoadingSection;
    private final ExceptionsSection exceptionsSection;
    private final SystemSection systemSection;
    private final SecuritySection securitySection;
    private final ContainerSection containerSection;
    private final ConfigurationSection configurationSection;
    private final ProfileManager profileManager;
    private final BoundedOperation<AutoAnalysisDashboard> autoAnalysisRuns;
    private final Supplier<? extends AutoCloseable> backgroundLease;
    private final AdvertisedFamilies advertised;

    /**
     * @param autoAnalysisRuns shared by every call, so a second compute request for the same profile
     *                         joins the run in flight and operations_status can follow it
     * @param backgroundLease  holds this profile open while a run outlives the call that started it
     * @param advertised       the families a next step may route to
     */
    public JvmMcpTools(
            ProfileManager profileManager,
            BoundedOperation<AutoAnalysisDashboard> autoAnalysisRuns,
            Supplier<? extends AutoCloseable> backgroundLease,
            AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.autoAnalysisRuns = autoAnalysisRuns;
        this.backgroundLease = backgroundLease;
        this.advertised = advertised;
        this.sections = JvmSections.standard(profileManager);
        // Taken from the registry rather than built again, so there is one instance of each and the
        // availability jvm_sections reports is the one each tool is gated on.
        this.autoAnalysisSection = sections.get(AutoAnalysisSection.ID, AutoAnalysisSection.class);
        this.gcSection = sections.get(GcSection.ID, GcSection.class);
        this.gcDetailSection = sections.get(GcDetailSection.ID, GcDetailSection.class);
        this.safepointsSection = sections.get(SafepointsSection.ID, SafepointsSection.class);
        this.jitSection = sections.get(JitSection.ID, JitSection.class);
        this.threadsSection = sections.get(ThreadsSection.ID, ThreadsSection.class);
        this.nativeMemorySection = sections.get(NativeMemorySection.ID, NativeMemorySection.class);
        this.classLoadingSection = sections.get(ClassLoadingSection.ID, ClassLoadingSection.class);
        this.exceptionsSection = sections.get(ExceptionsSection.ID, ExceptionsSection.class);
        this.systemSection = sections.get(SystemSection.ID, SystemSection.class);
        this.securitySection = sections.get(SecuritySection.ID, SecuritySection.class);
        this.containerSection = sections.get(ContainerSection.ID, ContainerSection.class);
        this.configurationSection = sections.get(ConfigurationSection.ID, ConfigurationSection.class);
    }

    @Tool(description = "Reports which machine-level dashboards this profile can answer - garbage "
            + "collection, safepoints, JIT compilation, threads, native memory, the container, the "
            + "JVM's configuration and Jeffrey's own auto analysis - each with the tool that renders it, "
            + "the event types it is built from and whether the recording carries them. A recording "
            + "only holds what the profiler was told to capture.")
    @McpToolMeta(cost = McpToolCost.CHEAP)
    @McpOutputSchema(SectionCatalogue.class)
    public McpToolResult sections() {
        String profileId = profileId();
        // Each row names its own tool, so the follow-up only says where every reader can start.
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(onProfile(AUTO_ANALYSIS_TOOL).why(START_WHY))
                .guidance(SECTIONS_GUIDANCE)
                .followUp();
        return McpToolResult.of(new SectionCatalogue(
                profileId, sections.availability(), followUp, UiLinks.view(profileId, SECTIONS_VIEW)));
    }

    @Tool(description = "Returns Jeffrey's Auto Analysis: the JMC rule set run over the whole "
            + "recording, as findings with a severity, an explanation and a suggested fix - the "
            + "cheapest broad look at a profile, each finding naming the call that carries its figures. "
            + "status is COMPUTED, NOT_COMPUTED or CANNOT_COMPUTE (no JFR recording file). Computed and "
            + "cached at import, so normally a cache read. For a profile that missed that, compute=true "
            + "runs it - slow and unbounded in memory, since it reads the whole recording through the "
            + "rule set. A compute run waits up to 45 s, then answers NOT_COMPUTED with an operationId "
            + "for operations_status - a client that declared the MCP tasks extension gets a task after "
            + "about 5 s instead; the findings arrive in that operation's result.")
    @McpToolHints(readOnly = false, idempotent = true)
    @McpToolMeta(cost = McpToolCost.SLOW, requires = McpToolRequirement.AUTO_ANALYSIS)
    @McpOutputSchema(AutoAnalysisSection.Answer.class)
    public McpToolOutcome autoAnalysis(
            @ToolParam(required = false, description = "Run the rule set now if it has not been run "
                    + "before. Rarely needed, since a profile is warmed at import. Off by default "
                    + "because it reads the entire recording through the JMC toolkit, which on a "
                    + "large one takes a while and holds a lot of heap. Ignored when the analysis is "
                    + "already computed, which is then simply returned")
            Boolean compute,
            McpCallContext call) {

        String profileId = profileId();
        // Read here, where the request is bound; the worker thread and a task's answer have none.
        String link = viewLink(autoAnalysisSection.view());
        AutoAnalysisStatus status = autoAnalysisSection.status();
        if (status == AutoAnalysisStatus.COMPUTED) {
            return McpToolResult.of(computed(reachable(autoAnalysisSection), null, link));
        }
        if (status == AutoAnalysisStatus.CANNOT_COMPUTE || !Boolean.TRUE.equals(compute)) {
            return McpToolResult.of(notComputed(status, link));
        }
        return autoAnalysisRuns.run(new BoundedOperation.Work<>(
                        profileId,
                        backgroundLease,
                        () -> {
                            profileManager.autoAnalysisManager().generate();
                            return reachable(autoAnalysisSection);
                        },
                        dashboard -> dashboard,
                        onProfile(AUTO_ANALYSIS_TOOL).with(COMPUTE, true).why(RETRY_WHY)),
                call,
                new BoundedOperation.Replies<>(
                        (dashboard, operation) -> computed(dashboard, operation, link),
                        operation -> computing(operation, link)));
    }

    @Tool(description = "Returns one of the garbage-collection pages beneath the jvm_gc overview: "
            + "CONFIGURATION, the TENURING distribution, the IHOP and MMU behind a concurrent cycle, G1's "
            + "regions and evacuation failures, ZGC's allocation stalls and relocations, the STRING_TABLES, "
            + "FINALIZERS, REFERENCES processing, the parallel PHASES, or PLAB statistics - their tables, "
            + "with times as UTC epoch milliseconds. A per-collection table keeps 25 rows: the most recent "
            + "for tenuring, otherwise the worst by its own figure; omittedRows says how many it left out. "
            + "Most pages are collector-specific and empty under the other collectors. With no page it "
            + "lists the pages.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(GcDetailSection.Answer.class)
    public McpToolResult gcDetail(
            @ToolParam(required = false, description = "Which page to render, one of the names this tool "
                    + "lists. Omit for the list of pages.")
            GcDetailPage page) {

        MicroscopeView view = page == null ? gcDetailSection.view() : page.view();
        return McpToolResult.of(answer(gcDetailSection, view,
                () -> page == null ? gcDetailSection.render() : gcDetailSection.page(page),
                GcDetailSection.Answer::of));
    }

    @Tool(description = "Reports what the JVM loaded and who loaded it: classes currently loaded, "
            + "loaded and unloaded over the run, the metaspace they hold, the class loaders ranked "
            + "by what they carry, the slowest individual loads where the recording captured them, "
            + "and any redefinitions an agent made. Answers 'why is start-up slow' when no method "
            + "is, and is where a metaspace that keeps growing first shows itself.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(ClassLoadingSection.Answer.class)
    public McpToolResult classLoading() {
        return McpToolResult.of(answer(classLoadingSection, ClassLoadingSection.Answer::of));
    }

    @Tool(description = "Reports what the application threw: how many throwables in total, how many "
            + "were sampled with a stack, how many were Errors, and the types ranked by count with "
            + "their commonest messages. Constructing an exception walks the stack, so a type thrown "
            + "in a loop is a real cost that no flamegraph frame names. A large total with no types "
            + "listed means the throw events were not recorded, which the result says rather than "
            + "reporting nothing thrown.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(ExceptionsSection.Answer.class)
    public McpToolResult exceptions() {
        return McpToolResult.of(answer(exceptionsSection, ExceptionsSection.Answer::of));
    }

    @Tool(description = "Returns the machine underneath the JVM: machine CPU against this JVM's own, "
            + "what the difference leaves for everything else on the box, the peak context-switch "
            + "rate, the other processes running there and any this JVM started. Answers 'is it my "
            + "JVM or the box' - a profile whose own CPU is modest while the machine is saturated "
            + "describes an application being starved, and every flamegraph from it reads "
            + "differently once that is known.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(SystemSection.Answer.class)
    public McpToolResult system() {
        return McpToolResult.of(answer(systemSection, SystemSection.Answer::of));
    }

    @Tool(description = "Reports TLS, certificates and deserialization: how many handshakes and to how "
            + "many distinct peers, the protocols and ciphers negotiated, certificates that are "
            + "expired, expiring or weakly signed, and what was deserialized including anything a "
            + "filter rejected. Many handshakes for few peers means connections are not being "
            + "reused; the certificate findings are about the deployment rather than the code, and "
            + "are evidence of what the JVM actually presented.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(SecuritySection.Answer.class)
    public McpToolResult security() {
        return McpToolResult.of(answer(securitySection, SecuritySection.Answer::of));
    }

    @Tool(description = "Returns the garbage collection dashboard: the stop-the-world budget this "
            + "recording paid and how it was distributed, collections split by generation, what "
            + "caused them, how much was freed, and the longest individual collections. Pause "
            + "figures are sumOfPauses and longestPause, not the event duration, which for ZGC, "
            + "Shenandoah and G1's concurrent cycles covers phases the application ran straight "
            + "through. No GC event names the code producing the garbage - an allocation flamegraph "
            + "does.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(GcSection.Answer.class)
    public McpToolResult gc() {
        return McpToolResult.of(answer(gcSection, GcSection.Answer::of));
    }

    @Tool(description = "Returns the safepoints and VM operations dashboard: the pauses that are not "
            + "garbage collection. Every VM operation stops the application the same way a "
            + "collection does, and a thread slow to reach the safepoint holds every other thread "
            + "there. Answers 'GC looks fine and we still have pauses', and names the threads that "
            + "keep everyone else waiting with the state they were in - in Java means a loop the JIT "
            + "stripped the safepoint poll out of, in native means a call the JVM cannot interrupt.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(SafepointsSection.Answer.class)
    public McpToolResult safepoints() {
        return McpToolResult.of(answer(safepointsSection, SafepointsSection.Answer::of));
    }

    @Tool(description = "Returns the JIT compilation dashboard: compiler totals, the slowest "
            + "compilations, code cache occupancy, and deoptimisations aggregated by method and "
            + "reason. A method deoptimised repeatedly ran interpreted for part of the recording; a "
            + "code cache that ran full stopped compilation altogether. An empty compilation list "
            + "means nothing compiled slowly enough to cross the recording's threshold, not that "
            + "nothing compiled - the statistics are there either way.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(JitSection.Answer.class)
    public McpToolResult jit() {
        return McpToolResult.of(answer(jitSection, JitSection.Answer::of));
    }

    @Tool(description = "Returns the threads dashboard: how many threads there were and at peak, how "
            + "often they slept, parked and blocked on monitors, which threads burned the most user "
            + "and system CPU, which allocated the most bytes, and - for a Loom application - how "
            + "often a virtual thread pinned its carrier, for how long and why. A flamegraph "
            + "aggregates across threads; this is the per-thread attribution it hides.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(ThreadsSection.Answer.class)
    public McpToolResult threads() {
        return McpToolResult.of(answer(threadsSection, ThreadsSection.Answer::of));
    }

    @Tool(description = "Returns the native memory dashboard: resident set size and its growth, direct "
            + "byte buffers, loaded native libraries, and the Native Memory Tracking categories when "
            + "the JVM was started with NMT enabled. The half of a memory problem neither a "
            + "flamegraph nor a heap dump can see - a process killed for memory while the Java heap "
            + "looked healthy.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(NativeMemorySection.Answer.class)
    public McpToolResult nativeMemory() {
        return McpToolResult.of(answer(nativeMemorySection, NativeMemorySection.Answer::of));
    }

    @Tool(description = "Returns the container dashboard: the cgroup limits the JVM read at start-up "
            + "(CPU quota and period, effective processor count, memory limits) and whether the "
            + "scheduler throttled the process, with Jeffrey's verdict as a finding and the counters "
            + "behind it. Answers 'slow in the cluster, fine on my laptop' - CFS throttling parks every "
            + "thread once the quota is spent, which a CPU flamegraph cannot show.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(ContainerSection.Answer.class)
    public McpToolResult container() {
        return McpToolResult.of(answer(containerSection, ContainerSection.Answer::of));
    }

    @Tool(description = "Returns what the JVM was actually started with, in the labelled sections the "
            + "Jeffrey UI shows as tabs: application and JVM information, CPU and operating system, "
            + "the collector, heap, survivor, TLAB and young-generation settings, the compiler, the "
            + "container and the virtualisation. Without a section it lists the section names this "
            + "profile has. These are the values a tuning recommendation is measured against; "
            + "jvm_flags says where each flag's value came from.")
    @McpToolMeta(cost = McpToolCost.CHEAP)
    @McpOutputSchema(ConfigurationSection.Answer.class)
    public McpToolResult configuration(
            @ToolParam(required = false, description = "One section name as this tool lists it, e.g. "
                    + "GC_HEAP_CONFIGURATION. Omit for the list of sections.")
            ConfigurationTab section) {

        if (section == null || !sections.isAvailable(configurationSection)) {
            return McpToolResult.of(answer(configurationSection, ConfigurationSection.Answer::of));
        }
        Optional<ConfigurationDashboard> tab = configurationSection.section(section);
        if (tab.isEmpty()) {
            String profileId = profileId();
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(CONFIGURATION_TOOL).why(CONFIGURATION_LIST_WHY))
                    .followUp();
            return McpToolResult.of(ConfigurationSection.Answer.of(new SectionHeader(
                    SectionStatus.NOT_RECORDED, NO_SUCH_CONFIGURATION_SECTION.formatted(section.name()),
                    profileId, configurationSection.id(), configurationSection.title(), followUp,
                    viewLink(configurationSection.view())), null));
        }
        return McpToolResult.of(ConfigurationSection.Answer.of(recorded(configurationSection, tab.get()), tab.get()));
    }

    @Tool(description = "Analyses the thread dumps this recording captured, together: how many dumps "
            + "and when (UTC epoch milliseconds), peak thread count, the deadlocks found, the monitors "
            + "threads were queueing on, the threads stuck across consecutive dumps, and the frames that "
            + "appear most often - and which dump index holds each. Answers 'it stopped responding': "
            + "a deadlock or a thread pool all blocked on one lock is visible here and in nothing "
            + "else. A deadlock found in several dumps is one row with its occurrences. Cut to fit the "
            + "answer, the dumps keep the most recent and the stuck threads the longest stuck, and "
            + "omittedRows counts the cut. status is NO_THREAD_DUMPS when the recording holds none.")
    @McpToolMeta(cost = McpToolCost.MODERATE)
    @McpOutputSchema(ThreadDumps.class)
    public McpToolResult threadDumps() {
        String profileId = profileId();
        String link = viewLink(THREAD_DUMPS_VIEW);
        ThreadDumpAnalysis analysis = profileManager.threadManager().threadDumpAnalysis();
        if (analysis == null || analysis.header().dumpCount() == 0) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(THREADS_TOOL).why(THREADS_WHY))
                    .guidance(NO_THREAD_DUMPS_GUIDANCE)
                    .followUp();
            return McpToolResult.of(new ThreadDumps(ThreadDumpsStatus.NO_THREAD_DUMPS, NO_THREAD_DUMPS,
                    profileId, null, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), followUp, link));
        }

        Optional<RecordingSpan> span = RecordingSpan.of(profileManager.info());
        ThreadDumpAnalysis.Header header = analysis.header();
        List<Dump> dumps = analysis.dumps().stream()
                .map(dump -> new Dump(dump.index(), epochAt(span, dump.timeOffsetMillis()), dump.threadCount(),
                        dump.deadlockCount()))
                .toList();
        List<RecurringDeadlock> deadlocks = recurring(analysis.deadlocks(), span);
        List<StuckThread> stuck = analysis.stuckThreads().stream()
                .map(thread -> new StuckThread(thread.name(), thread.state(), thread.topFrame(),
                        thread.consecutiveDumps(), thread.stuckForMillis()))
                .sorted(LONGEST_STUCK_FIRST)
                .toList();
        List<LockContention> contention = analysis.lockContention().stream()
                .map(lock -> new LockContention(lock.monitorId(), lock.monitorClass(), lock.waiterCount(), lock.owner()))
                .toList();
        List<ThreadDumpAnalysis.FrameStat> topFrames = analysis.topFrames();

        NextSteps.Builder next = NextSteps.builder(advertised);
        if (!deadlocks.isEmpty()) {
            next.next(onProfile(THREAD_DUMP_TOOL)
                    .with(INDEX, deadlocks.getFirst().firstDumpIndex())
                    .with(STATE, ThreadState.BLOCKED)
                    .why(DEADLOCK_WHY));
        } else if (!dumps.isEmpty()) {
            next.next(onProfile(THREAD_DUMP_TOOL).with(INDEX, dumps.getFirst().index()).why(FIRST_DUMP_WHY));
        }
        McpFollowUp followUp = next
                .nextWhen(!contention.isEmpty() || !stuck.isEmpty(), onProfile(BLOCKING_MONITORS_TOOL).why(MONITORS_WHY))
                .guidanceWhen(!stuck.isEmpty(), STUCK_GUIDANCE)
                .followUp();
        DumpsHeader dumpsHeader = new DumpsHeader(header.dumpCount(), header.peakThreadCount(), header.deadlockCount(),
                header.stuckThreadCount(), epochAt(span, header.firstOffsetMillis()),
                epochAt(span, header.lastOffsetMillis()));

        // Up to two hundred dumps and a stuck thread per waiting worker can outgrow the answer limit, so
        // every table shows at most the same number of rows - the most that fit - and the cut is counted.
        // What survives a cut is what a hang is read from: the latest dumps and the longest-stuck threads.
        IntFunction<ThreadDumps> page = rows -> {
            Map<String, Integer> omitted = new LinkedHashMap<>();
            return new ThreadDumps(ThreadDumpsStatus.OK, null, profileId, dumpsHeader,
                    lastRows(DUMPS_TABLE, dumps, rows, omitted),
                    firstRows(DEADLOCKS_TABLE, deadlocks, rows, omitted),
                    firstRows(LOCK_CONTENTION_TABLE, contention, rows, omitted),
                    firstRows(STUCK_THREADS_TABLE, stuck, rows, omitted),
                    firstRows(TOP_FRAMES_TABLE, topFrames, rows, omitted),
                    Map.copyOf(omitted), followUp, link);
        };
        int longest = Math.max(Math.max(dumps.size(), deadlocks.size()),
                Math.max(Math.max(contention.size(), stuck.size()), topFrames.size()));
        ThreadDumps answer = longest == 0
                ? page.apply(0)
                : FittingPage.largest(longest, page, JvmMcpTools::fits).orElseGet(() -> page.apply(0));
        return McpToolResult.of(answer);
    }

    /**
     * A deadlock the JVM never broke is found again in every later dump. Reported once, at its first
     * dump, with how many dumps held it and when it was last seen, so persistence reads as a figure
     * rather than as two hundred identical rows.
     */
    private static List<RecurringDeadlock> recurring(
            List<ThreadDumpAnalysis.DeadlockEntry> entries, Optional<RecordingSpan> span) {
        Map<Cycle, List<ThreadDumpAnalysis.DeadlockEntry>> byCycle = new LinkedHashMap<>();
        for (ThreadDumpAnalysis.DeadlockEntry entry : entries) {
            byCycle.computeIfAbsent(Cycle.of(entry), cycle -> new ArrayList<>()).add(entry);
        }
        return byCycle.values().stream()
                .map(seen -> new RecurringDeadlock(
                        seen.getFirst().dumpIndex(),
                        epochAt(span, seen.getFirst().timeOffsetMillis()),
                        epochAt(span, seen.getLast().timeOffsetMillis()),
                        seen.size(),
                        seen.getFirst().description(),
                        seen.getFirst().involvedThreads() == null ? List.of() : seen.getFirst().involvedThreads()))
                .toList();
    }

    /** The last rows of a table, in order, noting in {@code omitted} how many earlier ones it left out. */
    private static <T> List<T> lastRows(String table, List<T> rows, int limit, Map<String, Integer> omitted) {
        if (rows.size() > limit) {
            omitted.put(table, rows.size() - limit);
            return rows.subList(rows.size() - limit, rows.size());
        }
        return rows;
    }

    /** The first rows of a table, noting in {@code omitted} how many it left out. */
    private static <T> List<T> firstRows(String table, List<T> rows, int limit, Map<String, Integer> omitted) {
        if (rows.size() > limit) {
            omitted.put(table, rows.size() - limit);
            return rows.subList(0, limit);
        }
        return rows;
    }

    @Tool(description = "Returns one thread dump by index: its threads with their state and stack, and "
            + "the deadlocks the JVM detected in it; jvm_threadDumps says which index holds the "
            + "deadlock or the stuck threads. The first 50 threads unless limit says otherwise; "
            + "state narrows to one thread state, and omittedThreads counts what the answer left out. "
            + "status is NO_SUCH_DUMP for an index the profile does not have.")
    @McpToolMeta(cost = McpToolCost.CHEAP)
    @McpOutputSchema(ThreadDump.class)
    public McpToolResult threadDump(
            @ToolParam(required = true, description = "Index of the dump, as listed by jvm_threadDumps")
            Integer index,
            @ToolParam(required = false, description = "Only threads in this state, e.g. BLOCKED for a lock "
                    + "convoy or WAITING for a pool with nothing to do. Omit for every thread.")
            ThreadState state,
            @ToolParam(required = false, description = "Maximum number of threads to return (default: "
                    + DEFAULT_DUMP_THREADS + ", max: " + MAX_DUMP_THREADS + ")")
            @ToolParamBounds(defaultValue = DEFAULT_DUMP_THREADS, min = 1, max = MAX_DUMP_THREADS)
            Integer limit) {

        if (index == null) {
            throw new IllegalArgumentException("index is required; jvm_threadDumps lists the dumps");
        }
        String profileId = profileId();
        String link = viewLink(THREAD_DUMPS_VIEW);
        ParsedDump dump = profileManager.threadManager().threadDump(index);
        if (dump == null) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(THREAD_DUMPS_TOOL).why(THREAD_DUMPS_WHY))
                    .followUp();
            return McpToolResult.of(new ThreadDump(ThreadDumpStatus.NO_SUCH_DUMP, NO_SUCH_DUMP.formatted(index),
                    profileId, index, null, 0, 0, 0, List.of(), List.of(), followUp, link));
        }

        List<DumpThread> matching = dump.threads().stream()
                .filter(thread -> state == null || thread.state() == state)
                .map(JvmMcpTools::dumpThread)
                .toList();
        int bound = ToolArguments.boundedLimit(limit, DEFAULT_DUMP_THREADS, MAX_DUMP_THREADS);
        Long capturedAt = epochAt(RecordingSpan.of(profileManager.info()), dump.timeOffsetMillis());
        List<DumpDeadlock> deadlocks = dump.deadlocks().stream()
                .map(deadlock -> new DumpDeadlock(index, capturedAt, deadlock.description(), deadlock.involvedThreads()))
                .toList();
        int wanted = Math.min(bound, matching.size());
        // A dump of a busy server with deep stacks can outgrow the answer limit well inside 500
        // threads, so the page is the most threads that fit, and the rest are counted, not cut.
        IntFunction<ThreadDump> page = shown -> new ThreadDump(
                ThreadDumpStatus.OK, null, profileId, index, capturedAt, dump.threads().size(), matching.size(),
                matching.size() - shown, matching.subList(0, shown), deadlocks,
                threadDumpFollowUp(index, state, bound, matching, shown), link);
        ThreadDump answer = wanted == 0
                ? page.apply(0)
                : FittingPage.largest(wanted, page, JvmMcpTools::fits).orElseGet(() -> page.apply(0));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Returns the JVM flags this run actually used, grouped by where each value came "
            + "from - a default, the command line, or the JVM's own ergonomics - with each change's "
            + "time as UTC epoch milliseconds. The only place that distinguishes a flag someone set from "
            + "one the JVM chose; a deployment manifest is evidence of neither. jvm_configuration reports "
            + "the resulting collector and heap settings; this reports the switches. status is NO_FLAGS "
            + "when the recording carries no flag events.")
    @McpToolMeta(cost = McpToolCost.CHEAP)
    @McpOutputSchema(Flags.class)
    public McpToolResult flags() {
        String profileId = profileId();
        String link = viewLink(FLAGS_VIEW);
        FlagsData flags = profileManager.flagsManager().getAllFlags();
        if (flags == null || flags.totalFlags() == 0) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(onProfile(CONFIGURATION_TOOL).why(CONFIGURATION_WHY))
                    .followUp();
            return McpToolResult.of(new Flags(FlagsStatus.NO_FLAGS, NO_FLAGS, profileId, 0, 0, Map.of(), 0,
                    followUp, link));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(onProfile(GC_TOOL).why(FLAGS_GC_WHY))
                .next(onProfile(JIT_TOOL).why(FLAGS_JIT_WHY))
                .guidance(ORIGIN_GUIDANCE)
                .followUp();
        List<OriginFlag> ordered = new ArrayList<>();
        flags.flagsByOrigin().forEach((origin, details) ->
                details.forEach(detail -> ordered.add(new OriginFlag(origin, flag(detail)))));
        // Every flag the JVM knows, with its description, can outgrow the answer limit; the groups are
        // ordered command line first and defaults last, so a cut drops defaults before anything set.
        Flags answer = FittingPage.largest(ordered.size(),
                        shown -> flagsAnswer(flags, ordered, shown, profileId, followUp, link), JvmMcpTools::fits)
                .orElseGet(() -> flagsAnswer(flags, ordered, 0, profileId, followUp, link));
        return McpToolResult.of(answer);
    }

    /**
     * A section's answer: the dashboard with its routing when the recording carries the section's
     * events, else {@link SectionStatus#NOT_RECORDED} with the events it needed. The page is the
     * section's own.
     */
    private <D extends Record, A extends Record> A answer(
            JvmSection<D> section, BiFunction<SectionHeader, D, A> answer) {
        return answer(section, section.view(), section::render, answer);
    }

    /**
     * @param notRecordedView the page a NOT_RECORDED answer links, which a page asked for by name decides
     */
    private <D extends Record, A extends Record> A answer(JvmSection<D> section, MicroscopeView notRecordedView,
            Supplier<D> render, BiFunction<SectionHeader, D, A> answer) {
        if (!sections.isAvailable(section)) {
            return answer.apply(notRecorded(section, notRecordedView), null);
        }
        D dashboard = section.reachable(render.get(), advertised::servesTool);
        return answer.apply(recorded(section, dashboard), dashboard);
    }

    /** A rendered dashboard's header, linking the page that dashboard is best seen on. */
    private <D extends Record> SectionHeader recorded(JvmSection<D> section, D dashboard) {
        NextSteps.Builder next = NextSteps.builder(advertised);
        section.followUp(next, dashboard);
        return new SectionHeader(SectionStatus.OK, null, profileId(), section.id(), section.title(),
                next.followUp(), viewLink(section.view(dashboard)));
    }

    private SectionHeader notRecorded(JvmSection<?> section, MicroscopeView view) {
        String eventTypes = String.join(EVENT_TYPE_SEPARATOR, section.eventTypes().stream()
                .map(Type::code)
                .sorted()
                .toList());
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(onProfile(SECTIONS_TOOL).why(SECTIONS_WHY))
                .guidance(NOT_RECORDED_GUIDANCE.formatted(eventTypes))
                .followUp();
        return new SectionHeader(SectionStatus.NOT_RECORDED, NOT_RECORDED.formatted(section.title(), eventTypes),
                profileId(), section.id(), section.title(), followUp, viewLink(view));
    }

    private AutoAnalysisDashboard reachable(AutoAnalysisSection section) {
        return section.reachable(section.render(), advertised::servesTool);
    }

    private AutoAnalysisSection.Answer computed(
            AutoAnalysisDashboard dashboard, McpOperationRegistry.Snapshot operation, String link) {
        NextSteps.Builder next = NextSteps.builder(advertised);
        autoAnalysisSection.followUp(next, dashboard);
        return new AutoAnalysisSection.Answer(AutoAnalysisStatus.COMPUTED, null, profileId(),
                autoAnalysisSection.id(), autoAnalysisSection.title(), dashboard,
                operation == null ? null : operation.operationId(), operation, next.followUp(), link);
    }

    private AutoAnalysisSection.Answer notComputed(AutoAnalysisStatus status, String link) {
        boolean computable = status == AutoAnalysisStatus.NOT_COMPUTED;
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(computable, onProfile(AUTO_ANALYSIS_TOOL).with(COMPUTE, true).why(COMPUTE_WHY))
                .next(onProfile(SECTIONS_TOOL).why(OTHER_SECTIONS_WHY))
                .followUp();
        return new AutoAnalysisSection.Answer(status, computable ? NOT_COMPUTED : CANNOT_COMPUTE, profileId(),
                autoAnalysisSection.id(), autoAnalysisSection.title(), null, null, null, followUp, link);
    }

    private AutoAnalysisSection.Answer computing(McpOperationRegistry.Snapshot operation, String link) {
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(McpNextTool.call(OPERATIONS_STATUS_TOOL).with(OPERATION_ID, operation.operationId())
                        .why(POLL_WHY))
                .followUp();
        return new AutoAnalysisSection.Answer(AutoAnalysisStatus.NOT_COMPUTED, COMPUTING, profileId(),
                autoAnalysisSection.id(), autoAnalysisSection.title(), null, operation.operationId(), operation,
                followUp, link);
    }

    private McpFollowUp threadDumpFollowUp(
            int index, ThreadState state, int bound, List<DumpThread> matching, int shown) {
        boolean omitted = shown < matching.size();
        boolean blocked = matching.stream().anyMatch(thread -> thread.state() == ThreadState.BLOCKED);
        return NextSteps.builder(advertised)
                .nextWhen(omitted && state == null && blocked, onProfile(THREAD_DUMP_TOOL)
                        .with(INDEX, index)
                        .with(STATE, ThreadState.BLOCKED)
                        .why(BLOCKED_WHY))
                .nextWhen(omitted && shown == bound && bound < MAX_DUMP_THREADS, onProfile(THREAD_DUMP_TOOL)
                        .with(INDEX, index)
                        .with(STATE, state)
                        .with(LIMIT, MAX_DUMP_THREADS)
                        .why(MORE_THREADS_WHY))
                .nextWhen(blocked, onProfile(BLOCKING_MONITORS_TOOL).why(MONITORS_WHY))
                .followUp();
    }

    private static Flags flagsAnswer(
            FlagsData flags, List<OriginFlag> ordered, int shown, String profileId, McpFollowUp followUp,
            String link) {
        Map<String, List<Flag>> byOrigin = new LinkedHashMap<>();
        for (OriginFlag entry : ordered.subList(0, shown)) {
            byOrigin.computeIfAbsent(entry.origin(), origin -> new ArrayList<>()).add(entry.flag());
        }
        return new Flags(FlagsStatus.OK, null, profileId, flags.totalFlags(), flags.changedFlags(), byOrigin,
                ordered.size() - shown, followUp, link);
    }

    private static Flag flag(JvmFlagDetail detail) {
        List<FlagChange> history = detail.changeHistory() == null ? List.of() : detail.changeHistory().stream()
                .map(JvmMcpTools::flagChange)
                .toList();
        return new Flag(detail.name(), detail.value(), detail.type(), detail.origin(),
                detail.previousValues() == null ? List.of() : detail.previousValues(), detail.hasChanged(),
                detail.description(), history);
    }

    private static FlagChange flagChange(FlagValueChange change) {
        return new FlagChange(change.value(), change.timestamp());
    }

    private static DumpThread dumpThread(ParsedDump.ParsedThread thread) {
        return new DumpThread(thread.name(), thread.group(), thread.state(), thread.frames(),
                thread.locks().stream()
                        .map(lock -> new DumpLock(lock.kind(), lock.monitorId(), lock.monitorClass()))
                        .toList());
    }

    private static Long epochAt(Optional<RecordingSpan> span, long offsetMillis) {
        return span.map(recording -> recording.epochAt(offsetMillis)).orElse(null);
    }

    /** Whether an answer whose text is its own JSON stays inside the response limit. */
    private static boolean fits(Record answer) {
        return Json.toString(answer).length() <= McpToolOutput.MAX_CHARS;
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private McpNextTool.Call onProfile(String tool) {
        return McpNextTool.call(tool).with(PROFILE_ID, profileId());
    }

    /**
     * The dashboard's own page, for the reader rather than for the model - a URL carries nothing that
     * can be analysed, which is why it travels with an answer instead of behind a tool of its own.
     */
    private String viewLink(MicroscopeView view) {
        return UiLinks.view(profileId(), view);
    }

    /** Whether the thread dumps could be read. */
    enum ThreadDumpsStatus {
        /** The recording carries thread dumps, analysed together below. */
        OK,
        /** The recording carries no {@code jdk.ThreadDump} events. */
        NO_THREAD_DUMPS
    }

    /** Whether the dump asked for exists. */
    enum ThreadDumpStatus {
        /** The dump exists and its threads are below. */
        OK,
        /** The profile has no dump at that index. */
        NO_SUCH_DUMP
    }

    /** Whether the JVM's flags were recorded. */
    enum FlagsStatus {
        /** The recording carries flag events, grouped below by origin. */
        OK,
        /** The recording carries no flag events. */
        NO_FLAGS
    }

    /**
     * What {@code jvm_sections} answers.
     *
     * @param sections every section in the order a reader works through them
     */
    record SectionCatalogue(
            String profileId,
            List<JvmSections.SectionAvailability> sections,
            McpFollowUp followUp,
            @McpDescription("The profile's recorded event types in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * What {@code jvm_threadDumps} answers.
     *
     * @param header the dump count and span; null when there are no dumps
     */
    record ThreadDumps(
            ThreadDumpsStatus status,
            @McpNullable
            String reason,
            String profileId,
            @McpNullable
            DumpsHeader header,
            @McpDescription("The dumps in order; cut to fit, it keeps the most recent")
            List<Dump> dumps,
            List<RecurringDeadlock> deadlocks,
            List<LockContention> lockContention,
            @McpDescription("The threads stuck across consecutive dumps, stuck longest first, then across the "
                    + "most dumps; cut to fit, it keeps the worst")
            List<StuckThread> stuckThreads,
            List<ThreadDumpAnalysis.FrameStat> topFrames,
            @McpDescription("How many rows the size cap left out, keyed by table: dumps, deadlocks, "
                    + "lockContention, stuckThreads, topFrames; dumps keeps the most recent, stuckThreads the "
                    + "worst, the others their first rows; empty when nothing was cut")
            Map<String, Integer> omittedRows,
            McpFollowUp followUp,
            @McpDescription("The thread dumps page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * @param firstDumpEpochMs when the first dump was taken, as UTC epoch milliseconds; null when the
     *                         profile carries no recording span to place it on
     * @param lastDumpEpochMs  when the last one was
     */
    record DumpsHeader(
            int dumpCount,
            int peakThreadCount,
            int deadlockCount,
            int stuckThreadCount,
            @McpNullable
            Long firstDumpEpochMs,
            @McpNullable
            Long lastDumpEpochMs) {
    }

    /**
     * @param capturedAtEpochMs when the dump was taken, as UTC epoch milliseconds; null without a span
     */
    record Dump(
            int index,
            @McpNullable
            Long capturedAtEpochMs,
            int threadCount,
            int deadlockCount) {
    }

    /**
     * One deadlock cycle, however many dumps found it.
     *
     * @param firstDumpIndex    the first dump that holds it, which jvm_threadDump opens
     * @param capturedAtEpochMs when that dump was taken, as UTC epoch milliseconds; null without a span
     * @param lastSeenEpochMs   when the last dump holding it was taken
     * @param occurrences       how many dumps held it
     */
    record RecurringDeadlock(
            int firstDumpIndex,
            @McpNullable
            Long capturedAtEpochMs,
            @McpNullable
            Long lastSeenEpochMs,
            int occurrences,
            @McpNullable
            String description,
            List<String> involvedThreads) {
    }

    record DumpDeadlock(
            int dumpIndex,
            @McpNullable
            Long capturedAtEpochMs,
            @McpNullable
            String description,
            List<String> involvedThreads) {
    }

    record LockContention(
            @McpNullable
            String monitorId,
            @McpNullable
            String monitorClass,
            int waiterCount,
            @McpNullable
            String owner) {
    }

    record StuckThread(
            String name,
            ThreadState state,
            @McpNullable
            String topFrame,
            int consecutiveDumps,
            long stuckForMs) {
    }

    /**
     * What {@code jvm_threadDump} answers.
     *
     * @param capturedAtEpochMs when the dump was taken, as UTC epoch milliseconds; null when there is
     *                          no such dump or no span to place it on
     * @param matchingThreads   the threads in the requested state, or every thread when none was asked for
     * @param omittedThreads    how many matching threads are not shown, by the limit or the size cap
     */
    record ThreadDump(
            ThreadDumpStatus status,
            @McpNullable
            String reason,
            String profileId,
            int index,
            @McpNullable
            Long capturedAtEpochMs,
            int totalThreads,
            int matchingThreads,
            int omittedThreads,
            List<DumpThread> threads,
            List<DumpDeadlock> deadlocks,
            McpFollowUp followUp,
            @McpDescription("The thread dumps page in the Microscope UI, for the user")
            String uiLink) {
    }

    record DumpThread(
            String name,
            @McpNullable
            String group,
            ThreadState state,
            List<String> frames,
            List<DumpLock> locks) {
    }

    record DumpLock(
            ParsedDump.ThreadLock.Kind kind,
            @McpNullable
            String monitorId,
            @McpNullable
            String monitorClass) {
    }

    /**
     * What {@code jvm_flags} answers.
     *
     * @param flagsByOrigin the flags by where their value came from, command line first
     * @param omittedFlags  how many flags the size cap left out, defaults first
     */
    record Flags(
            FlagsStatus status,
            @McpNullable
            String reason,
            String profileId,
            int totalFlags,
            int changedFlags,
            Map<String, List<Flag>> flagsByOrigin,
            int omittedFlags,
            McpFollowUp followUp,
            @McpDescription("The flags page in the Microscope UI, for the user")
            String uiLink) {
    }

    record Flag(
            String name,
            @McpNullable
            String value,
            @McpNullable
            String type,
            @McpNullable
            String origin,
            List<String> previousValues,
            boolean hasChanged,
            @McpNullable
            String description,
            List<FlagChange> changeHistory) {
    }

    /**
     * @param changedAtEpochMs when the flag took this value, as UTC epoch milliseconds
     */
    record FlagChange(
            @McpNullable
            String value,
            long changedAtEpochMs) {
    }

    /** What makes two dumps' deadlocks the same one: the JVM's description and the threads, in any order. */
    private record Cycle(String description, Set<String> threads) {

        static Cycle of(ThreadDumpAnalysis.DeadlockEntry entry) {
            return new Cycle(entry.description(),
                    entry.involvedThreads() == null ? Set.of() : new TreeSet<>(entry.involvedThreads()));
        }
    }

    /** One flag in the order the answer is cut in, with the origin it is grouped under. */
    private record OriginFlag(String origin, Flag flag) {
    }
}
