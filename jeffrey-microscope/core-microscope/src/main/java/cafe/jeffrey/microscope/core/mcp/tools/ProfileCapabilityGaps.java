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

import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * What one profile cannot answer, said in words before anyone asks.
 * <p>
 * A recording holds only what the profiler was told to capture, and every tool in the surface
 * already reports its own corner of that — {@code flamegraph_list} names the groups with no samples,
 * {@code jvm_sections} the dashboards with no events, {@code profiles_features} the instrumentation
 * that was never there, {@code profiles_samplerHealth} the samples the kernel dropped. Spread over
 * four calls, the gaps are found one at a time, usually after a negative result has already been
 * reported as a clean one. This gathers them into one list on the summary, each with what it stops
 * the reader from concluding and what would close it next time.
 * <p>
 * Every line here is a fact about the recording, never a verdict about the application: a gap says
 * "this cannot be assessed from here", and nothing more.
 */
final class ProfileCapabilityGaps {

    /**
     * @param subject what is missing, as a short handle — a feature, a flamegraph group, a jvm_ section
     * @param gap     what the recording lacks and which tools that leaves empty
     * @param remedy  what would close the gap in the next recording, or how to read around it
     */
    record CapabilityGap(String subject, String gap, String remedy) {
    }

    private record FeatureGap(String gap, String remedy) {
    }

    private static final String SUBJECT_FORMAT = "format";
    private static final String SUBJECT_HEAP_DUMP = "heapDump";
    private static final String SUBJECT_SAMPLER = "sampler";
    private static final String SUBJECT_AUTO_ANALYSIS = "autoAnalysis";
    private static final String JVM_TOOL_PREFIX = "jvm_";
    private static final String EVENT_TYPE_SEPARATOR = ", ";

    private static final String REMEDY_INSTRUMENT =
            "Run the application with Jeffrey's instrumentation (jeffrey-agent) so the next recording carries them.";
    private static final String REMEDY_ENABLE_EVENTS =
            "Enable those event types in the recording settings for the next run.";

    private static final String FLAMEGRAPH_ONLY_GAP =
            "This profile is an imported %s sample set. It carries samples and stacks only: flamegraph_, "
                    + "compare_ and timeline_ apply, and the jfr_, jvm_, traces_, http_, jdbc_, grpc_, io_, "
                    + "blocking_ and memory_ families have no events to read.";
    private static final String FLAMEGRAPH_ONLY_REMEDY =
            "Record a JFR with the JDK or async-profiler when the question needs more than a call tree.";
    private static final String HEAP_DUMP_ONLY_GAP =
            "This profile is a heap dump. It answers what is retained and by whom (heap_), and holds no "
                    + "events: the jfr_, flamegraph_, jvm_, traces_ and the other recording families do not apply.";
    private static final String HEAP_DUMP_ONLY_REMEDY =
            "A JFR recording of the same interval, analysed as its own profile, answers who allocated it.";
    private static final String NO_HEAP_DUMP_GAP =
            "This profile has no heap dump, so the heap_ family does not apply: what is retained, the "
                    + "dominators and the GC-root paths need a dump.";
    private static final String NO_HEAP_DUMP_REMEDY =
            "Capture one (jcmd <pid> GC.heap_dump) and analyse it with recordings_analyzeFile as its own profile.";
    private static final String HEAP_DUMP_UNINDEXED_GAP =
            "The heap dump's index has not been built, so every heap_ tool answers empty until it is.";
    private static final String HEAP_DUMP_UNINDEXED_REMEDY =
            "heap_prepare builds it; heap_status says when it is done.";

    private static final String FLAMEGRAPH_GROUP_GAP =
            "The recording holds no %s (%s), so flamegraph_export of that event type returns an empty tree "
                    + "and that group cannot be assessed from this profile.";
    private static final String FLAMEGRAPH_GROUP_REMEDY =
            "Enable that event type in the profiler for the next recording; flamegraph_list names it.";

    private static final String JVM_SECTION_GAP =
            "No %s events were recorded (%s), so %s has nothing to render.";

    private static final String SAMPLER_GAP =
            "The CPU-time sampler dropped %,d of %,d samples (%.1f%%) in %,d loss events. Loss lands during "
                    + "the busiest moments, so every share taken from %s understates the hot paths.";
    private static final String SAMPLER_REMEDY =
            "Quote the loss share beside any CPU share from this profile, or record again with a lower "
                    + "sampling rate so fewer are dropped.";

    private static final String AUTO_ANALYSIS_MISSING_GAP =
            "The auto-analysis rule set did not run for this profile, so topFindings is empty for that reason "
                    + "and not because nothing was flagged. An import runs the rules before the profile is "
                    + "usable, so this is a run that failed.";
    private static final String AUTO_ANALYSIS_MISSING_REMEDY =
            "jvm_autoAnalysis with compute true runs it now; it reads the whole recording, which takes a while.";
    private static final String AUTO_ANALYSIS_IMPOSSIBLE_REMEDY =
            "The recording file is no longer available to Jeffrey, so the rules cannot be run for this profile.";

    private static final double PERCENT = 100.0;

    /**
     * One sentence per feature the profile lacks, naming the events it is gated on so the reader can
     * tell "never instrumented" from "instrumented, nothing happened". Features that describe the
     * installation rather than the recording (AI analysis) are not here, and a feature without an
     * entry is reported by name alone.
     */
    private static final Map<FeatureType, FeatureGap> FEATURE_GAPS = Map.ofEntries(
            entry(FeatureType.TRACES, new FeatureGap(
                    "This profile holds no traces, so the traces_ family has nothing and 'which request was "
                            + "slow' is answered only in aggregate by http_overview and jdbc_overview.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.HTTP_SERVER_DASHBOARD, new FeatureGap(
                    "No " + Type.HTTP_SERVER_EXCHANGE.code() + " events were recorded, so http_overview and "
                            + "http_endpoint with direction SERVER have nothing: the recording captured no "
                            + "served requests.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.HTTP_CLIENT_DASHBOARD, new FeatureGap(
                    "No " + Type.HTTP_CLIENT_EXCHANGE.code() + " events were recorded, so http_overview with "
                            + "direction CLIENT has nothing: no outbound HTTP calls were captured.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.GRPC_SERVER_DASHBOARD, new FeatureGap(
                    "No " + Type.GRPC_SERVER_EXCHANGE.code() + " events were recorded, so the grpc_ tools with "
                            + "direction SERVER have nothing.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.GRPC_CLIENT_DASHBOARD, new FeatureGap(
                    "No " + Type.GRPC_CLIENT_EXCHANGE.code() + " events were recorded, so the grpc_ tools with "
                            + "direction CLIENT have nothing.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.JDBC_STATEMENTS_DASHBOARD, new FeatureGap(
                    "No JDBC statement events were recorded, so jdbc_overview and jdbc_statementGroup have "
                            + "nothing: whether the database is the reason a request is slow cannot be read here.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.JDBC_POOL_DASHBOARD, new FeatureGap(
                    "No JDBC pool events were recorded, so jdbc_pools has nothing: a request waiting for a "
                            + "connection is invisible in this profile.",
                    REMEDY_INSTRUMENT)),
            entry(FeatureType.METHOD_TRACING_DASHBOARD, new FeatureGap(
                    "No " + Type.METHOD_TRACE.code() + " or " + Type.METHOD_TIMING.code() + " events were "
                            + "recorded, so the methodtracing_ family has nothing.",
                    "Enable jdk.MethodTrace and jdk.MethodTiming with a method filter (JEP 520) in the recording "
                            + "settings for the next run.")),
            entry(FeatureType.ASYNC_PROFILER_SPANS, new FeatureGap(
                    "No " + Type.SPAN.code() + " events were recorded, so traces_spanFlamegraphExport has no "
                            + "profiler spans to scope a flamegraph to.",
                    "Record with async-profiler's span support next time.")),
            entry(FeatureType.CONTAINER_DASHBOARD, new FeatureGap(
                    "No usable " + Type.CONTAINER_CONFIGURATION.code() + " and " + Type.CONTAINER_IO_USAGE.code()
                            + " events, so jvm_container cannot say whether the scheduler throttled the "
                            + "process: either the JVM did not run in a container, or those events were off.",
                    REMEDY_ENABLE_EVENTS)),
            entry(FeatureType.PERF_COUNTERS_DASHBOARD, new FeatureGap(
                    "No perf-counters file accompanied the recording, so the perf-counter dashboard is empty.",
                    "Upload the recording together with its perf-counters file next time.")),
            entry(FeatureType.SUBSECOND, new FeatureGap(
                    "This profile carries no per-sample timestamps, so timeline_zoom cannot resolve below a second.",
                    "Record a JFR or OTLP profile when the shape over time matters.")),
            entry(FeatureType.TIMESERIES, new FeatureGap(
                    "This profile carries no per-sample timestamps, so timeline_hotWindows cannot say when the "
                            + "samples landed.",
                    "Record a JFR or OTLP profile when the shape over time matters.")));

    private final ProfileManager profileManager;
    private final FlamegraphCatalog catalog;
    private final JvmSections sections;

    ProfileCapabilityGaps(ProfileManager profileManager, FlamegraphCatalog catalog) {
        this.profileManager = profileManager;
        this.catalog = catalog;
        this.sections = JvmSections.standard(profileManager);
    }

    /**
     * @param disabledFeatures the features the profile lacks, as {@code profiles_features} reports them
     */
    List<CapabilityGap> gaps(List<FeatureType> disabledFeatures) {
        RecordingEventSource source = profileManager.info().eventSource();
        List<CapabilityGap> gaps = new ArrayList<>();
        if (source == RecordingEventSource.HEAP_DUMP) {
            // A dump is not a recording: listing every JFR family as missing would bury the one
            // sentence that explains all of them.
            gaps.add(new CapabilityGap(SUBJECT_FORMAT, HEAP_DUMP_ONLY_GAP, HEAP_DUMP_ONLY_REMEDY));
            addHeapDumpGap(gaps, disabledFeatures);
            return List.copyOf(gaps);
        }
        if (source.isFlamegraphOnlyImport()) {
            gaps.add(new CapabilityGap(
                    SUBJECT_FORMAT, FLAMEGRAPH_ONLY_GAP.formatted(source.getLabel()), FLAMEGRAPH_ONLY_REMEDY));
            addFlamegraphGroupGaps(gaps);
            return List.copyOf(gaps);
        }
        addFeatureGaps(gaps, disabledFeatures);
        addHeapDumpGap(gaps, disabledFeatures);
        addFlamegraphGroupGaps(gaps);
        addJvmSectionGaps(gaps);
        addSamplerGap(gaps);
        addAutoAnalysisGap(gaps);
        return List.copyOf(gaps);
    }

    private static void addFeatureGaps(List<CapabilityGap> gaps, List<FeatureType> disabledFeatures) {
        for (FeatureType feature : disabledFeatures) {
            if (feature == FeatureType.HEAP_DUMP || feature == FeatureType.AI_ANALYSIS) {
                continue;
            }
            FeatureGap text = FEATURE_GAPS.get(feature);
            if (text == null) {
                gaps.add(new CapabilityGap(feature.name(), feature.name() + " is not available for this profile.", null));
            } else {
                gaps.add(new CapabilityGap(feature.name(), text.gap(), text.remedy()));
            }
        }
    }

    private void addHeapDumpGap(List<CapabilityGap> gaps, List<FeatureType> disabledFeatures) {
        if (!disabledFeatures.contains(FeatureType.HEAP_DUMP)) {
            return;
        }
        if (profileManager.heapDumpManager().heapDumpExists()) {
            gaps.add(new CapabilityGap(SUBJECT_HEAP_DUMP, HEAP_DUMP_UNINDEXED_GAP, HEAP_DUMP_UNINDEXED_REMEDY));
        } else {
            gaps.add(new CapabilityGap(SUBJECT_HEAP_DUMP, NO_HEAP_DUMP_GAP, NO_HEAP_DUMP_REMEDY));
        }
    }

    private void addFlamegraphGroupGaps(List<CapabilityGap> gaps) {
        for (FlamegraphPanel panel : catalog.notRecorded()) {
            gaps.add(new CapabilityGap(
                    panel.section(),
                    FLAMEGRAPH_GROUP_GAP.formatted(panel.title(), panel.event().code()),
                    FLAMEGRAPH_GROUP_REMEDY));
        }
    }

    private void addJvmSectionGaps(List<CapabilityGap> gaps) {
        // Two sections can be gated on the same events (the GC overview and its detail pages), and
        // one sentence about those events is enough: the second would only repeat the first.
        Set<List<String>> reported = new HashSet<>();
        for (JvmSections.SectionAvailability section : sections.availability()) {
            if (section.available() || !reported.add(section.eventTypes())) {
                continue;
            }
            String tool = JVM_TOOL_PREFIX + section.id();
            gaps.add(new CapabilityGap(
                    tool,
                    JVM_SECTION_GAP.formatted(
                            section.title(), String.join(EVENT_TYPE_SEPARATOR, section.eventTypes()), tool),
                    REMEDY_ENABLE_EVENTS));
        }
    }

    private void addSamplerGap(List<CapabilityGap> gaps) {
        CpuTimeSampleLoss loss = profileManager.samplerHealthManager().cpuTimeSampleLoss();
        if (loss == null || loss.lostSamples() == 0) {
            return;
        }
        long total = loss.capturedSamples() + loss.lostSamples();
        double lostShare = total == 0 ? 0 : loss.lostSamples() * PERCENT / total;
        gaps.add(new CapabilityGap(
                SUBJECT_SAMPLER,
                SAMPLER_GAP.formatted(
                        loss.lostSamples(), total, lostShare, loss.lossEvents(), Type.CPU_TIME_SAMPLE.code()),
                SAMPLER_REMEDY));
    }

    private void addAutoAnalysisGap(List<CapabilityGap> gaps) {
        AutoAnalysisManager autoAnalysis = profileManager.autoAnalysisManager();
        if (autoAnalysis.isComputed()) {
            return;
        }
        gaps.add(new CapabilityGap(
                SUBJECT_AUTO_ANALYSIS,
                AUTO_ANALYSIS_MISSING_GAP,
                autoAnalysis.canGenerate() ? AUTO_ANALYSIS_MISSING_REMEDY : AUTO_ANALYSIS_IMPOSSIBLE_REMEDY));
    }
}
