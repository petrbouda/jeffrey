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

import cafe.jeffrey.flamegraph.code.CodeAttribution;
import cafe.jeffrey.flamegraph.code.CodeAttribution.FocusCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodCost;
import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;
import cafe.jeffrey.flamegraph.code.CodeQuery;
import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.flamegraph.code.MethodRef;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.CodeStatus;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.FrameShare;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.LineShare;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.MatchedFrame;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.TierShare;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.ValueUnit;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionSiteStat;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysis;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The application's code, read from a profile by the names a reader holding its source uses: which of
 * their methods the samples are charged to, and everything measured about one method they are looking
 * at.
 * <p>
 * Jeffrey never reads the repository. The reader brings what only the checkout knows -- the package
 * prefixes of their own code, the class and method they have open -- and every answer comes back keyed
 * by the same names, with the runtime names it merged (lambda bodies, nested classes, Kotlin's generated
 * methods) listed beside each figure so the source it counts is never a guess.
 * <p>
 * The reads are the flamegraph's own: the same per-stack records, the same window and idle filter, so a
 * method's figures here and its frames in {@code flamegraph_export} agree, and each answer links the
 * graph with the method searched.
 */
public class CodeMcpTools {

    static final int DEFAULT_HOTSPOT_LIMIT = 15;
    static final int MAX_HOTSPOT_LIMIT = 50;

    /** How many rows of each list a code answer carries; the rest is counted, not listed. */
    private static final int MAX_LINES = 5;
    private static final int MAX_NEIGHBOURS = 8;
    private static final int MAX_MATCHED = 10;
    private static final int MAX_EXCEPTION_CLASSES = 10;

    /** How many event types {@code code_method} reads when none is named, busiest first. */
    static final int MAX_KINDS = 6;

    /** How many of the busiest trace operations {@code code_method} looks for the method in. */
    static final int MAX_OPERATIONS = 8;

    private static final String HOTSPOTS_TOOL = "code_hotspots";
    private static final String METHOD_TOOL = "code_method";
    private static final String LIST_TOOL = "flamegraph_list";
    private static final String RESOLVE_TOOL = "ide_resolve";
    private static final String OPERATION_FLAMEGRAPH_TOOL = "traces_operationFlamegraphExport";
    private static final String EXCEPTION_SITES_TOOL = "jvm_exceptionSites";
    private static final String PROFILE_ID = "profileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String FRAME_PREFIXES = "framePrefixes";
    private static final String CLASS_NAME = "className";
    private static final String METHOD_NAME = "methodName";
    private static final String START_EPOCH_MS = "startEpochMs";
    private static final String END_EPOCH_MS = "endEpochMs";
    private static final String NAME = "name";
    private static final String KIND = "kind";
    private static final String OPERATION_EVENT_TYPE = "eventType";
    private static final String GRAPH_EVENT_TYPE = "graphEventType";
    private static final String THROWN_CLASS = "thrownClass";

    private static final String METHOD_WHY = "everything measured about this method: callers, callees, lines, "
            + "exceptions and the requests that run it";
    private static final String EXPORT_SEARCH_GUIDANCE = "The whole call tree around it, when its shape "
            + "matters: flamegraph_export with eventType %s and search %s marks its frames.";
    private static final String RESOLVE_WHY = "where this method lives in the developer's editor";
    private static final String HOTSPOTS_WHY = "the application's own methods ranked by the cost charged to them";
    private static final String OPERATION_WHY = "this operation's call tree, where the method sits among its other work";
    private static final String SITES_WHY = "the throw sites of this class, each with the own frame that reached it";
    private static final String LIST_WHY = "every event type this profile recorded with stacks";

    private static final String PREFIXES_SUGGESTED_GUIDANCE = "framePrefixes were not given, so this read used "
            + "the busiest non-library packages of the profile (framePrefixesSource SUGGESTED). Read the root "
            + "packages off the source tree and pass them as framePrefixes; the ranking is only as good as "
            + "they are.";
    private static final String NOTHING_OWN_REASON = "No sample's stack held a frame under %s. The prefixes may "
            + "not match the classes as the JVM names them; suggestedFramePrefixes lists the busiest packages "
            + "this profile did sample.";
    private static final String NOT_RECORDED_REASON =
            "This profile recorded no %s samples with stacks. It recorded: %s.";
    private static final String NOTHING_RECORDED_REASON =
            "This profile has no event type with stacks, so no code can be read from it. It may be a heap "
                    + "dump (use the heap_* tools).";
    private static final String NOT_SEEN_REASON = "No sample's stack ran %s in the event types read (%s). In "
            + "this profile it was not hot or not called - or it runs under another name: nearestFrames lists "
            + "the sampled methods of the same class and the same name elsewhere.";
    private static final String NO_OPERATIONS_REASON = "The profile has no traces, so no request can be named.";
    private static final String NO_OPERATION_SAMPLES_REASON = "The profile recorded no CPU or wall-clock "
            + "samples to place the method inside a request.";
    private static final String OPERATIONS_EVENT_REASON = "Read on %s, the first stack-sampling event type of "
            + "this profile.";
    private static final String NO_THROWS_REASON = "The recording has no throw events (jdk.JavaExceptionThrow, "
            + "jdk.JavaErrorThrow).";
    private static final String MATCHING_GUIDANCE = "matchedFrames lists the runtime names merged into this "
            + "method - lambda bodies, nested and generated classes. Read each before attributing its cost to "
            + "the source in front of you.";

    /** The sample kinds operations are read on, preferred in this order when the profile has them. */
    static final List<Type> OPERATION_SAMPLE_TYPES = List.of(
            Type.EXECUTION_SAMPLE, Type.CPU_TIME_SAMPLE, Type.WALL_CLOCK_SAMPLE);

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public CodeMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Ranks the application's own methods by the cost charged to them: each sample "
            + "is charged to the deepest frame of your code on its stack, so the library, JDK and JVM work "
            + "below it lands on the method of yours that asked for it - the method you can change. Each "
            + "row has its charged, self and inclusive figures, the lines of the method the cost was taken "
            + "on, the frames below it the cost went into (delegatedTo) and a uiLink to the graph with the "
            + "method searched. framePrefixes are your source tree's root packages; omitted, the profile's "
            + "busiest non-library packages are used and the answer says so. Not a call tree "
            + "(flamegraph_export) and not a comparison (compare_methods). status NOT_RECORDED: no samples "
            + "of eventType; NOT_SEEN: no stack held a frame under framePrefixes.")
    @McpOutputSchema(Hotspots.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult hotspots(
            @ToolParam(required = true, description = "JFR event type to read, e.g. 'jdk.ExecutionSample' "
                    + "for CPU, 'jdk.ObjectAllocationSample' for allocation, 'jdk.JavaMonitorEnter' for lock "
                    + "contention. flamegraph_list names what this profile recorded.")
            String eventType,
            @ToolParam(required = false, description = "Your own package prefixes, e.g. [\"com.acme.\"] - "
                    + "the root packages of the source tree you are in. A trailing dot keeps com.acmegroup "
                    + "out. Omitted, the profile's busiest non-library packages are used.")
            List<String> framePrefixes,
            @ToolParam(required = false, description = "How many methods to return (default "
                    + DEFAULT_HOTSPOT_LIMIT + ", maximum " + MAX_HOTSPOT_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_HOTSPOT_LIMIT, min = 1, max = MAX_HOTSPOT_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit, with endEpochMs, for the whole recording.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit for the recording's own end.")
            Long endEpochMs,
            @ToolParam(required = false, description = "Count the event's weight (bytes allocated, nanoseconds "
                    + "blocked) instead of samples. Omitted: weighed for allocation and blocking, counted otherwise.")
            Boolean useWeight,
            @ToolParam(required = false, description = "Drop samples of threads that were idle")
            Boolean excludeIdle) {

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        ProfileInfo info = profileManager.info();
        Read read = Read.of(info, type, startEpochMs, endEpochMs, useWeight, excludeIdle);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_HOTSPOT_LIMIT, MAX_HOTSPOT_LIMIT);
        List<String> given = CodeAnswers.prefixes(framePrefixes);

        List<String> recorded = recordedTypes();
        if (!recorded.contains(type.code())) {
            return McpToolResult.of(Hotspots.notRecorded(info.id(), read, given, notRecordedReason(type, recorded),
                    NextSteps.builder(advertised)
                            .next(NextCalls.to(LIST_TOOL).with(PROFILE_ID, info.id()).why(LIST_WHY))
                            .followUp(),
                    UiLinks.view(info.id(), MicroscopeView.FLAMEGRAPHS_PRIMARY)));
        }

        PrefixSource source = given.isEmpty() ? PrefixSource.SUGGESTED : PrefixSource.GIVEN;
        List<String> prefixes = given;
        List<String> suggested = List.of();
        if (given.isEmpty()) {
            CodeAttribution everything = read.attribution(profileManager, CodeQuery.of(List.of(), List.of()));
            suggested = SuggestedPrefixes.from(everything.inclusiveByMethod());
            prefixes = suggested;
        }

        String graphLink = read.graphLink(info.id(), null);
        if (prefixes.isEmpty()) {
            return McpToolResult.of(Hotspots.nothingOwn(info.id(), read, prefixes, source, suggested,
                    NOTHING_OWN_REASON.formatted(prefixes), 0, NextSteps.builder(advertised).followUp(), graphLink));
        }

        CodeAttribution attribution = read.attribution(profileManager, CodeQuery.of(prefixes, List.of()));
        if (attribution.chargedToOwnCode() == 0) {
            List<String> busiest = suggested.isEmpty()
                    ? SuggestedPrefixes.from(attribution.inclusiveByMethod())
                    : suggested;
            return McpToolResult.of(Hotspots.nothingOwn(info.id(), read, prefixes, source, busiest,
                    NOTHING_OWN_REASON.formatted(prefixes), attribution.total(),
                    NextSteps.builder(advertised).followUp(), graphLink));
        }

        List<MethodCost> charged = attribution.ownMethods().stream()
                .filter(cost -> cost.charged() > 0)
                .toList();
        List<Hotspot> hotspots = new ArrayList<>();
        for (int i = 0; i < Math.min(rows, charged.size()); i++) {
            hotspots.add(Hotspot.of(i + 1, charged.get(i), attribution.total(),
                    read.graphLink(info.id(), CodeAnswers.search(charged.get(i).method()))));
        }

        MethodCost top = charged.getFirst();
        MethodRef topRef = MethodRef.of(top.method());
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(read.applyTo(NextCalls.to(METHOD_TOOL)
                                .with(PROFILE_ID, info.id())
                                .with(CLASS_NAME, topRef.className())
                                .with(METHOD_NAME, topRef.methodName())
                                .with(FRAME_PREFIXES, prefixes))
                        .with(EVENT_TYPE, type.code())
                        .why(METHOD_WHY))
                .nextWhen(advertised.has(AdvertisedFamilies.IDE), () -> NextCalls.to(RESOLVE_TOOL)
                        .with(PROFILE_ID, info.id())
                        .with(CLASS_NAME, top.method().className())
                        .with(METHOD_NAME, top.method().methodName())
                        .why(RESOLVE_WHY))
                .guidanceWhen(source == PrefixSource.SUGGESTED, PREFIXES_SUGGESTED_GUIDANCE)
                .followUp();

        return McpToolResult.of(new Hotspots(
                CodeStatus.OK, null, info.id(), type.code(), read.window(), read.weighted(), read.excludeIdle(),
                read.unit(), prefixes, source, suggested, attribution.total(), attribution.chargedToOwnCode(),
                CodeAnswers.pct(attribution.chargedToOwnCode(), attribution.total()),
                hotspots, charged.size() - hotspots.size(), followUp, graphLink));
    }

    @Tool(description = "Everything a profile measured about one method or class you are reading, across "
            + "every sample kind it recorded (or the event type named): inclusive and self figures with "
            + "their share, its callers and callees, the nearest of your own methods that reach it (with "
            + "framePrefixes), the lines inside it the samples were taken on, its JIT tiers, the exceptions "
            + "thrown with it on the stack and the traced operations whose requests run it. The runtime "
            + "names it matched - lambda bodies, nested classes, Kotlin's generated methods - are listed "
            + "beside each figure. Answers 'is this hot', 'who calls this', 'which requests run this'. "
            + "status NOT_SEEN is a measurement: no sample ran it, and nearestFrames names what did run "
            + "under a similar name.")
    @McpOutputSchema(MethodProfile.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE)
    public McpToolResult method(
            @ToolParam(required = true, description = "The class, fully qualified as in source "
                    + "(com.acme.Checkout, com.acme.Checkout.Pricing) or binary (com.acme.Checkout$Pricing)")
            String className,
            @ToolParam(required = false, description = "The method's name as written in source. Omit for the "
                    + "whole class and its nested classes.")
            String methodName,
            @ToolParam(required = false, description = "Your own package prefixes, e.g. [\"com.acme.\"]; "
                    + "adds ownCallers - the nearest of your methods above a library method")
            List<String> framePrefixes,
            @ToolParam(required = false, description = "Read only this event type. Omitted, every "
                    + "sample kind the profile recorded (CPU, wall clock, allocation, locks, method traces), "
                    + "busiest first, at most " + MAX_KINDS + ". Any type recorded with stacks can be named.")
            String eventType,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit, with endEpochMs, for the whole recording.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit for the recording's own end.")
            Long endEpochMs) {

        MethodRef ref = new MethodRef(
                ToolArguments.required(className, CLASS_NAME, "Pass the class as it is written in source."),
                methodName);
        ProfileInfo info = profileManager.info();
        List<String> prefixes = CodeAnswers.prefixes(framePrefixes);
        List<EventSummaryResult> recorded = recordedSummaries();
        List<Type> types = typesToRead(eventType, recorded, sampleKinds());
        EpochWindow window = Read.requestedWindow(info, startEpochMs, endEpochMs);
        String classLink = UiLinks.view(info.id(), MicroscopeView.FLAMEGRAPHS_PRIMARY);

        if (types.isEmpty()) {
            String reason = eventType == null || recorded.isEmpty()
                    ? NOTHING_RECORDED_REASON
                    : notRecordedReason(FlamegraphMcpTools.requireEventType(eventType), codes(recorded));
            return McpToolResult.of(MethodProfile.empty(CodeStatus.NOT_RECORDED, reason, info.id(), ref, window,
                    prefixes, NextSteps.builder(advertised)
                            .next(NextCalls.to(LIST_TOOL).with(PROFILE_ID, info.id()).why(LIST_WHY))
                            .followUp(),
                    classLink));
        }

        CodeQuery query = CodeQuery.of(prefixes, List.of(ref));
        List<KindRead> reads = new ArrayList<>();
        List<MethodValue> nearestPool = new ArrayList<>();
        for (Type type : types) {
            Read read = Read.of(info, type, startEpochMs, endEpochMs, null, false);
            CodeAttribution attribution = read.attribution(profileManager, query);
            FocusCost focus = attribution.focus().getFirst();
            reads.add(new KindRead(read,
                    SampleKind.of(read, attribution.total(), focus, read.graphLink(info.id(), CodeAnswers.search(ref)))));
            // Every kind read: a method another kind never ran (a lock wait's pool holds no pricing code)
            // still has its classmates in the kinds that did.
            nearestPool.addAll(attribution.inclusiveByMethod());
        }
        // The kinds that ran the method first, the largest share of its kind first: the read a reader
        // should look at heads the list, and is the one the next calls repeat.
        reads.sort(Comparator.comparingDouble((KindRead kind) -> kind.answer().inclusivePct()).reversed());
        List<SampleKind> kinds = reads.stream().map(KindRead::answer).toList();
        Read firstSeen = reads.getFirst().answer().inclusive() > 0 ? reads.getFirst().read() : null;

        Exceptions exceptions = exceptions(ref);
        if (firstSeen == null) {
            String readTypes = String.join(", ", types.stream().map(Type::code).toList());
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .nextWhen(!prefixes.isEmpty(), () -> NextCalls.to(HOTSPOTS_TOOL)
                            .with(PROFILE_ID, info.id())
                            .with(EVENT_TYPE, types.getFirst().code())
                            .with(FRAME_PREFIXES, prefixes)
                            .why(HOTSPOTS_WHY))
                    .followUp();
            return McpToolResult.of(new MethodProfile(CodeStatus.NOT_SEEN,
                    NOT_SEEN_REASON.formatted(ref.displayName(), readTypes), info.id(), ref.displayName(),
                    ref.className(), ref.methodName(), window, prefixes, kinds, nearest(ref, nearestPool),
                    exceptions, Operations.notRead(), followUp, kinds.getFirst().uiLink()));
        }

        Operations operations = operations(ref, query, recorded, window);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(advertised.has(AdvertisedFamilies.IDE), () -> resolveCall(info.id(), ref))
                .nextWhen(!operations.operations().isEmpty(), () -> operationCall(info.id(), operations))
                .nextWhen(!exceptions.thrown().isEmpty(), () -> NextCalls.to(EXCEPTION_SITES_TOOL)
                        .with(PROFILE_ID, info.id())
                        .with(THROWN_CLASS, exceptions.thrown().getFirst().thrownClass())
                        .with(FRAME_PREFIXES, prefixes.isEmpty() ? List.of(ref.className()) : prefixes)
                        .why(SITES_WHY))
                .guidance(MATCHING_GUIDANCE)
                .guidance(advertised.hint(AdvertisedFamilies.FLAMEGRAPH,
                        EXPORT_SEARCH_GUIDANCE.formatted(firstSeen.type().code(), CodeAnswers.search(ref))))
                .followUp();
        String uiLink = kinds.getFirst().uiLink();
        return McpToolResult.of(new MethodProfile(CodeStatus.OK, null, info.id(), ref.displayName(),
                ref.className(), ref.methodName(), window, prefixes, kinds, List.of(), exceptions, operations,
                followUp, uiLink));
    }

    private McpNextTool resolveCall(String profileId, MethodRef ref) {
        McpNextTool.Call call = NextCalls.to(RESOLVE_TOOL)
                .with(PROFILE_ID, profileId)
                .with(CLASS_NAME, ref.className());
        if (ref.methodName() != null) {
            call.with(METHOD_NAME, ref.methodName());
        }
        return call.why(RESOLVE_WHY);
    }

    private McpNextTool operationCall(String profileId, Operations operations) {
        OperationShare top = operations.operations().getFirst();
        return NextCalls.to(OPERATION_FLAMEGRAPH_TOOL)
                .with(PROFILE_ID, profileId)
                .with(NAME, top.operation())
                .with(KIND, top.kind())
                .with(OPERATION_EVENT_TYPE, top.eventType())
                .with(GRAPH_EVENT_TYPE, operations.sampledEventType())
                .why(OPERATION_WHY);
    }

    /** One event type's read and its answer, kept together while the answers are ordered. */
    private record KindRead(Read read, SampleKind answer) {
    }

    /**
     * The event types {@code code_method} reads: the one asked for, which may be any type recorded with
     * stacks, or else the busiest recorded sample kinds -- CPU, wall clock, allocation, locks, method
     * traces -- not every event that happens to carry a stack (a throw, a deoptimization).
     */
    private static List<Type> typesToRead(
            String eventType, List<EventSummaryResult> recorded, List<EventSummaryResult> sampleKinds) {
        if (eventType != null && !eventType.isBlank()) {
            Type type = FlamegraphMcpTools.requireEventType(eventType);
            return codes(recorded).contains(type.code()) ? List.of(type) : List.of();
        }
        return sampleKinds.stream()
                .sorted(Comparator.comparingLong((EventSummaryResult summary) -> summary.primary().samples())
                        .reversed())
                .limit(MAX_KINDS)
                .map(summary -> Type.fromCode(summary.code()))
                .toList();
    }

    /** The methods nearest to a reference nothing ran, by their runtime names. */
    private static List<String> nearest(MethodRef ref, List<MethodValue> inclusiveByMethod) {
        return CodeAnswers.nearest(ref, inclusiveByMethod).stream()
                .map(value -> value.method().displayName())
                .distinct()
                .toList();
    }

    /** The exceptions thrown with the referenced code on the throw stack, over the whole recording. */
    private Exceptions exceptions(MethodRef ref) {
        ThrowAnalysis analysis = profileManager.exceptionsManager().analysis();
        if (analysis.types().isEmpty()) {
            return new Exceptions(SectionState.NOT_RECORDED, NO_THROWS_REASON, List.of(), 0);
        }
        MethodRef.Matcher matcher = ref.matcher();
        Map<String, Long> byClass = new LinkedHashMap<>();
        for (ExceptionSiteStat site : analysis.sites()) {
            boolean onStack = analysis.stackOf(site).stream()
                    .anyMatch(frame -> matches(matcher, frame));
            if (onStack) {
                byClass.merge(site.thrownClass(), site.count(), Long::sum);
            }
        }
        List<Thrown> thrown = byClass.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new Thrown(entry.getKey(), entry.getValue()))
                .toList();
        List<Thrown> shown = ToolArguments.firstOf(thrown, MAX_EXCEPTION_CLASSES);
        return new Exceptions(SectionState.READ, null, shown, thrown.size() - shown.size());
    }

    private static boolean matches(MethodRef.Matcher matcher, ThrowFrame frame) {
        if (frame.className() == null || frame.methodName() == null) {
            return false;
        }
        return matcher.matches(new MethodKey(frame.className(), frame.methodName()));
    }

    /**
     * The busiest traced operations whose requests ran the method, read on the profile's first
     * stack-sampling type inside each operation's own spans.
     */
    private Operations operations(MethodRef ref, CodeQuery query, List<EventSummaryResult> recorded, EpochWindow window) {
        List<TraceOperationRow> busiest = profileManager.traceManager()
                .operations(TraceOperationListQuery.busiest(MAX_OPERATIONS))
                .operations();
        if (busiest.isEmpty()) {
            return new Operations(SectionState.NOT_RECORDED, NO_OPERATIONS_REASON, null, List.of());
        }
        Set<String> codes = Set.copyOf(codes(recorded));
        Optional<Type> sampled = OPERATION_SAMPLE_TYPES.stream()
                .filter(type -> codes.contains(type.code()))
                .findFirst();
        if (sampled.isEmpty()) {
            return new Operations(SectionState.NOT_RECORDED, NO_OPERATION_SAMPLES_REASON, null, List.of());
        }

        ProfileInfo info = profileManager.info();
        Type type = sampled.get();
        RelativeTimeRange range = FlamegraphMcpTools.timeRange(info, window);
        List<OperationShare> rows = new ArrayList<>();
        for (TraceOperationRow operation : busiest) {
            SpanScope scope = new SpanScope.Operation(operation.name(), operation.kind(), operation.eventType());
            CodeAttribution attribution = profileManager.flamegraphManager().codeAttribution(
                    CodeAnswers.params(type, range, false, false, scope), query);
            FocusCost focus = attribution.focus().getFirst();
            if (focus.seen()) {
                rows.add(new OperationShare(operation.name(), operation.kind(), operation.eventType(),
                        focus.inclusive(), attribution.total(), CodeAnswers.pct(focus.inclusive(), attribution.total()),
                        operation.count(), operation.p95Nanos(),
                        TraceLinks.operation(info.id(), operation.name(), operation.kind(), operation.eventType(), null)));
            }
        }
        rows.sort(Comparator.comparingDouble(OperationShare::sharePct).reversed());
        return new Operations(SectionState.READ, OPERATIONS_EVENT_REASON.formatted(type.code()), type.code(), rows);
    }

    /** The recorded sample kinds a flamegraph grid offers, each with samples. */
    private List<EventSummaryResult> sampleKinds() {
        return profileManager.flamegraphManager().eventSummaries().stream()
                .filter(summary -> summary.primary() != null && summary.primary().samples() > 0)
                .toList();
    }

    private List<EventSummaryResult> recordedSummaries() {
        return profileManager.flamegraphManager().graphableEventSummaries().stream()
                .filter(summary -> summary.primary() != null && summary.primary().samples() > 0)
                .toList();
    }

    private List<String> recordedTypes() {
        return codes(recordedSummaries());
    }

    private static List<String> codes(List<EventSummaryResult> summaries) {
        return summaries.stream().map(EventSummaryResult::code).toList();
    }

    private static String notRecordedReason(Type type, List<String> recorded) {
        if (recorded.isEmpty()) {
            return NOTHING_RECORDED_REASON;
        }
        return NOT_RECORDED_REASON.formatted(type.code(), String.join(", ", recorded));
    }

    /**
     * One code read as asked: the event type, the window checked against the recording, the weighting
     * and the idle filter. The graph it links and the calls it hands on repeat exactly these.
     */
    record Read(Type type, EpochWindow window, EpochWindow graphed, RelativeTimeRange range, boolean weighted,
                boolean excludeIdle) {

        static Read of(ProfileInfo info, Type type, Long startEpochMs, Long endEpochMs, Boolean useWeight,
                       Boolean excludeIdle) {
            EpochWindow window = requestedWindow(info, startEpochMs, endEpochMs);
            EpochWindow graphed = window != null
                    ? window
                    : RecordingSpan.of(info).map(RecordingSpan::whole).orElse(null);
            boolean weighted = useWeight != null ? useWeight : CodeAnswers.weightedByDefault(type);
            return new Read(type, window, graphed, FlamegraphMcpTools.timeRange(info, graphed), weighted,
                    Boolean.TRUE.equals(excludeIdle));
        }

        static EpochWindow requestedWindow(ProfileInfo info, Long startEpochMs, Long endEpochMs) {
            if (startEpochMs == null && endEpochMs == null) {
                return null;
            }
            return RecordingSpan.require(info).window(startEpochMs, endEpochMs);
        }

        ValueUnit unit() {
            return ValueUnit.of(type, weighted);
        }

        CodeAttribution attribution(ProfileManager manager, CodeQuery query) {
            return manager.flamegraphManager().codeAttribution(
                    CodeAnswers.params(type, range, weighted, excludeIdle, null), query);
        }

        /** The flamegraph of this read, with the given search marked; null search marks nothing. */
        String graphLink(String profileId, String search) {
            return FlamegraphViewLink.primary(profileId, type)
                    .weighted(weighted)
                    .excludeIdle(excludeIdle)
                    .window(graphed)
                    .search(search)
                    .url();
        }

        /** This read's window added to a call, so the next read covers the same stretch. */
        McpNextTool.Call applyTo(McpNextTool.Call call) {
            if (window != null) {
                call.with(START_EPOCH_MS, window.startEpochMs()).with(END_EPOCH_MS, window.endEpochMs());
            }
            return call;
        }
    }

    /** Where the prefixes a ranking used came from. */
    enum PrefixSource {
        /** The reader passed them, read off their source tree. */
        GIVEN,
        /** None was passed; the profile's busiest non-library packages were used. */
        SUGGESTED
    }

    /** Whether a section of an answer was read, or why it could not be. */
    enum SectionState {
        READ,
        NOT_RECORDED,
        NOT_READ
    }

    /**
     * The application's own methods by the cost charged to them.
     *
     * @param framePrefixes          the prefixes the ranking used
     * @param suggestedFramePrefixes the profile's busiest non-library packages, when they were computed
     */
    record Hotspots(
            CodeStatus status,
            @McpNullable
            @McpDescription("Why there is no ranking; null when status is OK")
            String reason,
            String profileId,
            String eventType,
            @McpNullable
            @McpDescription("The window read; null for the whole recording")
            EpochWindow window,
            boolean weighted,
            boolean excludeIdle,
            ValueUnit unit,
            List<String> framePrefixes,
            PrefixSource framePrefixesSource,
            @McpDescription("The profile's busiest non-library packages; empty when framePrefixes were given "
                    + "and matched")
            List<String> suggestedFramePrefixes,
            @McpDescription("Every sample (or unit of weight) read")
            long total,
            @McpDescription("The part of total whose stack held a frame of your code")
            long chargedToOwnCode,
            @McpDescription("chargedToOwnCode as a share of total, in percent; the rest ran no code of yours")
            double ownSharePct,
            @McpDescription("Your methods, highest charged cost first")
            List<Hotspot> methods,
            @McpDescription("Methods with a charged cost beyond the limit, counted rather than listed")
            int omittedMethods,
            McpFollowUp followUp,
            @McpDescription("The flamegraph of the same samples in Microscope, for the user")
            String uiLink) {

        static Hotspots notRecorded(String profileId, Read read, List<String> prefixes, String reason,
                                    McpFollowUp followUp, String uiLink) {
            return new Hotspots(CodeStatus.NOT_RECORDED, reason, profileId, read.type().code(), read.window(),
                    read.weighted(), read.excludeIdle(), read.unit(), prefixes,
                    prefixes.isEmpty() ? PrefixSource.SUGGESTED : PrefixSource.GIVEN, List.of(), 0, 0, 0.0,
                    List.of(), 0, followUp, uiLink);
        }

        static Hotspots nothingOwn(String profileId, Read read, List<String> prefixes, PrefixSource source,
                                   List<String> suggested, String reason, long total, McpFollowUp followUp,
                                   String uiLink) {
            return new Hotspots(CodeStatus.NOT_SEEN, reason, profileId, read.type().code(), read.window(),
                    read.weighted(), read.excludeIdle(), read.unit(), prefixes, source, suggested, total, 0, 0.0,
                    List.of(), 0, followUp, uiLink);
        }
    }

    /**
     * One of your methods.
     */
    record Hotspot(
            int rank,
            @McpDescription("Class#method, as the flamegraph exports name it")
            String frame,
            @McpDescription(CodeAnswers.REFERENCE_DESCRIPTION)
            String reference,
            String className,
            String methodName,
            @McpDescription("Samples whose deepest frame of your code is this method: its self time plus "
                    + "the library, JDK and JVM work below it. Adds up across methods to chargedToOwnCode")
            long charged,
            @McpDescription("charged as a share of total, in percent")
            double chargedPct,
            @McpDescription("Samples taken in this method itself")
            long self,
            @McpDescription("Samples with this method anywhere on the stack, once per stack")
            long inclusive,
            @McpDescription("inclusive as a share of total, in percent")
            double inclusivePct,
            @McpDescription("Lines of this method the charged samples were taken on, highest first; share of "
                    + "charged. Empty when the recording kept no line numbers")
            List<LineShare> lines,
            @McpDescription("The frames directly below this method its charged cost went into, highest "
                    + "first; share of charged. What is left is self")
            List<FrameShare> delegatedTo,
            @McpDescription("How this method ran: JIT tiers by share of its inclusive samples")
            List<TierShare> tiers,
            @McpDescription("The flamegraph with this method searched, for the user")
            String uiLink) {

        static Hotspot of(int rank, MethodCost cost, long total, String uiLink) {
            return new Hotspot(rank, cost.method().displayName(), MethodRef.of(cost.method()).displayName(),
                    cost.method().className(),
                    cost.method().methodName(), cost.charged(), CodeAnswers.pct(cost.charged(), total), cost.self(),
                    cost.inclusive(), CodeAnswers.pct(cost.inclusive(), total),
                    CodeAnswers.lines(cost.chargedLines(), cost.charged(), MAX_LINES),
                    CodeAnswers.frames(cost.delegatedTo(), cost.charged(), MAX_NEIGHBOURS),
                    CodeAnswers.tiers(cost.tiers()), uiLink);
        }
    }

    /**
     * Everything measured about one method or class.
     */
    record MethodProfile(
            CodeStatus status,
            @McpNullable
            @McpDescription("Why the method has no figures; null when status is OK")
            String reason,
            String profileId,
            @McpDescription("The reference as read: Class#method, or the class alone")
            String reference,
            String className,
            @McpNullable
            @McpDescription("The method; null when the whole class was read")
            String methodName,
            @McpNullable
            @McpDescription("The window read; null for the whole recording")
            EpochWindow window,
            List<String> framePrefixes,
            @McpDescription("One read per event type, those that ran the method first, by its share")
            List<SampleKind> kinds,
            @McpDescription("When nothing ran the reference: sampled methods of the same class or of the same "
                    + "name, busiest first - a rename, an overload or a package move shows here")
            List<String> nearestFrames,
            Exceptions exceptions,
            Operations operations,
            McpFollowUp followUp,
            @McpDescription("The flamegraph with this method searched, for the user")
            String uiLink) {

        static MethodProfile empty(CodeStatus status, String reason, String profileId, MethodRef ref,
                                   EpochWindow window, List<String> prefixes, McpFollowUp followUp, String uiLink) {
            return new MethodProfile(status, reason, profileId, ref.displayName(), ref.className(),
                    ref.methodName(), window, prefixes, List.of(), List.of(),
                    new Exceptions(SectionState.NOT_READ, null, List.of(), 0), Operations.notRead(), followUp, uiLink);
        }
    }

    /**
     * The method read on one event type.
     */
    record SampleKind(
            String eventType,
            ValueUnit unit,
            @McpDescription("Every sample (or unit of weight) of this type read")
            long total,
            @McpDescription("Samples with the method anywhere on the stack, once per stack")
            long inclusive,
            double inclusivePct,
            @McpDescription("Samples taken in the method itself")
            long self,
            double selfPct,
            @McpDescription("The runtime methods the reference matched, with their inclusive figure")
            List<MatchedFrame> matchedFrames,
            @McpDescription("Frames directly above the outermost matched frame; share of inclusive")
            List<FrameShare> callers,
            @McpDescription("Frames directly below the innermost matched frame; share of inclusive. What is "
                    + "left is self")
            List<FrameShare> callees,
            @McpDescription("The nearest frame of your code above it, per stack; share of inclusive. Empty "
                    + "without framePrefixes")
            List<FrameShare> ownCallers,
            @McpDescription("Lines of the innermost matched frame the samples were taken on; share of inclusive")
            List<LineShare> lines,
            List<TierShare> tiers,
            @McpDescription("The flamegraph of this type with the method searched, for the user")
            String uiLink) {

        static SampleKind of(Read read, long total, FocusCost focus, String uiLink) {
            long inclusive = focus.inclusive();
            return new SampleKind(read.type().code(), read.unit(), total, inclusive,
                    CodeAnswers.pct(inclusive, total), focus.self(), CodeAnswers.pct(focus.self(), total),
                    CodeAnswers.matched(focus.matchedFrames(), MAX_MATCHED),
                    CodeAnswers.frames(focus.callers(), inclusive, MAX_NEIGHBOURS),
                    CodeAnswers.frames(focus.callees(), inclusive, MAX_NEIGHBOURS),
                    CodeAnswers.frames(focus.ownCallers(), inclusive, MAX_NEIGHBOURS),
                    CodeAnswers.lines(focus.lines(), inclusive, MAX_LINES),
                    CodeAnswers.tiers(focus.tiers()), uiLink);
        }
    }

    /**
     * The exceptions thrown while the method was on the stack, over the whole recording.
     */
    record Exceptions(
            SectionState state,
            @McpNullable
            @McpDescription("Why nothing was read; null when state is READ")
            String reason,
            @McpDescription("Thrown classes, most throws first")
            List<Thrown> thrown,
            @McpDescription("Further thrown classes, counted rather than listed")
            int omittedClasses) {
    }

    record Thrown(String thrownClass, long count) {
    }

    /**
     * The busiest traced operations whose requests ran the method.
     */
    record Operations(
            SectionState state,
            @McpNullable
            @McpDescription("What the section was read on, or why it was not")
            String reason,
            @McpNullable
            @McpDescription("The sampled event type the method was looked for inside each operation")
            String sampledEventType,
            @McpDescription("Operations whose spans held the method, highest share first")
            List<OperationShare> operations) {

        static Operations notRead() {
            return new Operations(SectionState.NOT_READ, null, null, List.of());
        }
    }

    /**
     * One traced operation the method ran inside.
     */
    record OperationShare(
            String operation,
            String kind,
            String eventType,
            @McpDescription("Samples inside the operation's spans with the method on the stack")
            long samples,
            @McpDescription("Every sample inside the operation's spans")
            long operationSamples,
            @McpDescription("samples as a share of operationSamples, in percent")
            double sharePct,
            @McpDescription("How many times the operation ran")
            long operationCount,
            long p95Nanos,
            @McpDescription("The operation in Microscope, for the user")
            String uiLink) {
    }
}
