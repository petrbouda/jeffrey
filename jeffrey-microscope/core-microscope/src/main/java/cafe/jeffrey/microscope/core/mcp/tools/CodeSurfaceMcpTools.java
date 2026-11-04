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

import cafe.jeffrey.flamegraph.code.CallSiteQuery;
import cafe.jeffrey.flamegraph.code.CallSites;
import cafe.jeffrey.flamegraph.code.CallSites.CallSite;
import cafe.jeffrey.flamegraph.code.CallSites.OperationEntry;
import cafe.jeffrey.flamegraph.code.CodeAttribution;
import cafe.jeffrey.flamegraph.code.CodeQuery;
import cafe.jeffrey.flamegraph.code.FrameScope;
import cafe.jeffrey.flamegraph.code.MethodKey;
import cafe.jeffrey.flamegraph.code.MethodRef;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.CodeStatus;
import cafe.jeffrey.microscope.core.mcp.tools.CodeAnswers.FrameShare;
import cafe.jeffrey.microscope.core.mcp.tools.CodeMcpTools.PrefixSource;
import cafe.jeffrey.microscope.core.mcp.tools.CodeMcpTools.Read;
import cafe.jeffrey.microscope.core.mcp.tools.CodeMcpTools.SectionState;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceIds;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
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
 * What the application did at runtime, tied to the code of the repository the reader holds: where a
 * statement, a call or a lock was issued from, and which handler a request entered the code through.
 * <p>
 * Two ways in, and every answer says which it used. An event recorded with its stack (Jeffrey's JDBC
 * statements, the JDK's socket, file and lock events, an application's own JFR events) names its call
 * site directly: the deepest own frame on the stack and its line. An event recorded without one (the
 * HTTP and gRPC exchanges, trace spans) is tied to code through the samples taken inside its spans:
 * the outermost own frame there is where the request entered the code, which is inferred, not recorded.
 */
public class CodeSurfaceMcpTools {

    static final int DEFAULT_SITE_LIMIT = 15;
    static final int MAX_SITE_LIMIT = 50;

    /** How often one request has to issue the same thing, by the median, to be called repeated. */
    static final int REPEATED_PER_TRACE = 5;

    /** How much of a long group value (a statement's SQL) an answer carries; the full length beside it. */
    static final int MAX_GROUP_VALUE_CHARS = 200;

    private static final int MAX_OPERATIONS = 10;
    private static final int MAX_STATEMENTS = 15;
    private static final int MAX_SITES_PER_ROW = 3;
    private static final int MAX_CODE_PER_OPERATION = 3;
    private static final int MAX_ATTRIBUTE_KEYS = 20;
    private static final int MAX_NOTIFICATIONS = 15;
    private static final int MAX_CUSTOM_TYPES = 20;

    private static final String LINE_SEPARATOR = ":";

    /** The statement field call sites are split by when the caller names none. */
    private static final String STATEMENT_NAME_FIELD = "name";

    /** Event types that are the JDK's, Jeffrey's or a profiler's own rather than the application's. */
    private static final Set<String> PLATFORM_PREFIXES = Set.of("jdk.", "jeffrey.", "profiler.");

    /**
     * The samples a request is placed in code with, preferred in this order: wall-clock first, because it
     * samples a request's thread whether it runs or waits, so a short request that a CPU sampler catches
     * once or never is still seen.
     */
    private static final List<Type> REQUEST_SAMPLE_TYPES = List.of(
            Type.WALL_CLOCK_SAMPLE, Type.EXECUTION_SAMPLE, Type.CPU_TIME_SAMPLE);

    /**
     * Attribute keys in the order a reader of the business wants them: what the developer attached
     * first, then the event types' own fields, then the columns every span and notification has.
     */
    private static final List<String> KEY_SOURCE_ORDER = List.of(
            TraceAttributeSource.ATTRIBUTE.name(), TraceAttributeSource.NOTIFICATION_ATTRIBUTE.name(),
            TraceAttributeSource.EVENT_FIELD.name(), TraceAttributeSource.SPAN_SHAPE.name(),
            TraceAttributeSource.NOTIFICATION_SHAPE.name());

    private static final List<Type> JDBC_TYPES = List.of(
            Type.JDBC_QUERY, Type.JDBC_INSERT, Type.JDBC_UPDATE, Type.JDBC_DELETE, Type.JDBC_EXECUTE, Type.JDBC_STREAM);

    private static final String METHOD_TOOL = "code_method";
    private static final String CALL_SITES_TOOL = "code_callSites";
    private static final String LIST_TOOL = "flamegraph_list";
    private static final String DESCRIBE_TOOL = "jfr_describeEventType";
    private static final String TRACE_EXPORT_TOOL = "traces_traceExport";
    private static final String PROFILE_ID = "profileId";
    private static final String EVENT_TYPE = "eventType";
    private static final String CLASS_NAME = "className";
    private static final String METHOD_NAME = "methodName";
    private static final String FRAME_PREFIXES = "framePrefixes";
    private static final String GROUP_BY = "groupBy";
    private static final String TRACE_ID = "traceId";

    private static final String METHOD_WHY = "everything measured about the method that issues the most";
    private static final String REPEATED_WHY = "one request that issued it this many times, span by span";
    private static final String ENTRY_WHY = "everything measured about the handler the busiest operation enters through";
    private static final String STATEMENTS_WHY = "every call site of this statement type, counted per request";
    private static final String DESCRIBE_WHY = "the fields of this event type, to group or filter by";
    private static final String LIST_WHY = "every event type this profile recorded with stacks";

    private static final String NOT_RECORDED_REASON = "This profile recorded no %s events. %s";
    private static final String NO_STACKS_REASON = "This profile recorded %s without stack traces, so its events "
            + "cannot name a line of code. Jeffrey's HTTP and gRPC exchanges and its trace spans are recorded "
            + "that way; code_runtimeSurface ties their operations to code through the samples taken inside "
            + "their spans instead.";
    private static final String NOT_SEEN_REASON = "No %s event's stack held a frame under %s.";
    private static final String SUGGESTED_GUIDANCE = "framePrefixes were not given, so the profile's busiest "
            + "non-library packages were used (framePrefixesSource SUGGESTED). Pass your source tree's root "
            + "packages for a reading of your code.";
    private static final String REPEATED_GUIDANCE = "repeatedPerTrace marks a site one request issues at least "
            + "%s times by the median: a loop around a statement or a call, the shape of an N+1 query. Read the "
            + "line in its loop before calling it one.";
    private static final String ENTRY_INFERRED_NOTE = "An operation's entry points come from the stacks of the "
            + "statements its requests issued where it issued any (entryBasis EVENT_STACK, recorded), otherwise "
            + "from the %s samples taken inside its spans (SAMPLED_SPANS, inferred from where samples landed); "
            + "topMethods always from those samples.";
    private static final String NO_OPERATIONS_NOTE = "The profile has no traces, so no operation can be named.";
    private static final String NO_SAMPLES_NOTE = "The profile recorded no CPU or wall-clock samples of your code, "
            + "so only operations whose requests issued statements with stacks are placed in code (entryBasis "
            + "EVENT_STACK); topMethods are empty.";
    private static final String NO_STATEMENTS_NOTE = "No JDBC statement was recorded with its stack.";
    private static final String STATEMENTS_NOTE = "Statements name their call sites from their own stacks.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public CodeSurfaceMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns where events of one type were issued from in your own code: each event's "
            + "deepest frame of yours and its line, split by one of the event's fields (a statement's name or "
            + "sql, a host, a custom event's field), with count, total and longest duration, and - for events "
            + "inside traces - how many requests issued it and how many times one request did (perTraceMax, "
            + "perTraceMedian; repeatedPerTrace marks an N+1 shape), with exemplar traces. Answers 'which line "
            + "issues this SQL' and 'where is the N+1'. Works for any event type recorded with stacks: "
            + "jeffrey.Jdbc* statements, jdk.SocketRead, jdk.FileRead, jdk.JavaMonitorEnter, your own JFR "
            + "events. status NO_STACKS: the type was recorded without stacks (HTTP, gRPC, spans - see "
            + "code_runtimeSurface); NOT_RECORDED; NOT_SEEN: no stack held your code.")
    @McpOutputSchema(CallSitesAnswer.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult callSites(
            @ToolParam(required = true, description = "Event type to read, e.g. 'jeffrey.JdbcQuery', "
                    + "'jdk.SocketRead', or your own event. flamegraph_list names the types recorded with stacks.")
            String eventType,
            @ToolParam(required = false, description = "Your own package prefixes, e.g. [\"com.acme.\"]. Omitted, "
                    + "the profile's busiest non-library packages are used.")
            List<String> framePrefixes,
            @ToolParam(required = false, description = "The event field to split call sites by: 'name' (the "
                    + "default for JDBC statements), 'sql', 'host', or any field jfr_describeEventType lists. "
                    + "Omitted for any other type: one row per call site.")
            String groupBy,
            @ToolParam(required = false, description = "With filterValue: keep only events whose field holds "
                    + "that value, e.g. 'group' for one statement group")
            String filterField,
            @ToolParam(required = false, description = "The value filterField must hold")
            String filterValue,
            @ToolParam(required = false, description = "How many call sites to return (default "
                    + DEFAULT_SITE_LIMIT + ", maximum " + MAX_SITE_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_SITE_LIMIT, min = 1, max = MAX_SITE_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Start of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit, with endEpochMs, for the whole recording.")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window, as UTC epoch milliseconds inside "
                    + "the recording. Omit for the recording's own end.")
            Long endEpochMs) {

        Type type = FlamegraphMcpTools.requireEventType(eventType);
        ProfileInfo info = profileManager.info();
        Read read = Read.of(info, type, startEpochMs, endEpochMs, null, false);
        String group = groupBy == null || groupBy.isBlank()
                ? (JDBC_TYPES.contains(type) ? STATEMENT_NAME_FIELD : null)
                : groupBy.strip();
        String filter = filterField == null || filterField.isBlank() ? null : filterField.strip();
        if ((filter == null) != (filterValue == null)) {
            throw new IllegalArgumentException("filterField and filterValue go together: name the field and the "
                    + "value it must hold, or neither.");
        }
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_SITE_LIMIT, MAX_SITE_LIMIT);
        List<String> given = CodeAnswers.prefixes(framePrefixes);
        String typeLink = read.graphLink(info.id(), null);

        Set<String> withStacks = Set.copyOf(codes(profileManager.flamegraphManager().graphableEventSummaries()));
        if (!withStacks.contains(type.code())) {
            boolean recorded = codes(profileManager.flamegraphManager().allEventSummaries()).contains(type.code());
            String reason = recorded
                    ? NO_STACKS_REASON.formatted(type.code())
                    : NOT_RECORDED_REASON.formatted(type.code(), "flamegraph_list names the types recorded with stacks.");
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(NextCalls.to(LIST_TOOL).with(PROFILE_ID, info.id()).why(LIST_WHY))
                    .followUp();
            return McpToolResult.of(CallSitesAnswer.empty(recorded ? CodeStatus.NO_STACKS : CodeStatus.NOT_RECORDED,
                    reason, info.id(), read, given, given.isEmpty() ? PrefixSource.SUGGESTED : PrefixSource.GIVEN,
                    List.of(), group, filter, filterValue, 0, 0, 0, followUp,
                    UiLinks.view(info.id(), MicroscopeView.FLAMEGRAPHS_PRIMARY)));
        }

        PrefixSource source = given.isEmpty() ? PrefixSource.SUGGESTED : PrefixSource.GIVEN;
        List<String> suggested = given.isEmpty()
                ? SuggestedPrefixes.from(read.attribution(profileManager, CodeQuery.of(List.of(), List.of()))
                        .inclusiveByMethod())
                : List.of();
        List<String> prefixes = given.isEmpty() ? suggested : given;

        CallSites sites = profileManager.flamegraphManager().callSites(
                CodeAnswers.params(type, read.range(), false, false, null),
                new CallSiteQuery(FrameScope.of(prefixes), group, filter, filterValue));
        if (sites.sites().isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(NextCalls.to(DESCRIBE_TOOL).with(PROFILE_ID, info.id()).with(EVENT_TYPE, type.code())
                            .why(DESCRIBE_WHY))
                    .followUp();
            return McpToolResult.of(CallSitesAnswer.empty(CodeStatus.NOT_SEEN,
                    NOT_SEEN_REASON.formatted(type.code(), prefixes), info.id(), read, prefixes, source, suggested,
                    group, filter, filterValue, sites.totalEvents(), sites.totalNanos(), sites.unattributedEvents(),
                    followUp, typeLink));
        }

        List<Site> shown = sites.sites().stream()
                .limit(rows)
                .map(site -> Site.of(site, info.id(), read))
                .toList();
        Site top = shown.getFirst();
        MethodRef topRef = MethodRef.of(new MethodKey(top.className(), top.methodName()));
        Optional<Site> repeated = shown.stream().filter(Site::repeatedPerTrace).findFirst();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(NextCalls.to(METHOD_TOOL)
                        .with(PROFILE_ID, info.id())
                        .with(CLASS_NAME, topRef.className())
                        .with(METHOD_NAME, topRef.methodName())
                        .with(FRAME_PREFIXES, prefixes)
                        .why(METHOD_WHY))
                .nextWhen(repeated.isPresent() && !repeated.get().exemplarTraces().isEmpty(),
                        () -> NextCalls.to(TRACE_EXPORT_TOOL)
                                .with(PROFILE_ID, info.id())
                                .with(TRACE_ID, repeated.get().exemplarTraces().getFirst().traceId())
                                .why(REPEATED_WHY))
                .guidanceWhen(repeated.isPresent(), () -> REPEATED_GUIDANCE.formatted(REPEATED_PER_TRACE))
                .guidanceWhen(source == PrefixSource.SUGGESTED, SUGGESTED_GUIDANCE)
                .followUp();
        return McpToolResult.of(new CallSitesAnswer(CodeStatus.OK, null, info.id(), type.code(), read.window(),
                prefixes, source, suggested, group, filter, filterValue, sites.totalEvents(), sites.totalNanos(),
                sites.unattributedEvents(), shown, sites.sites().size() - shown.size(), followUp, typeLink));
    }

    @Tool(description = "Returns what the application did at runtime, tied to your code where the profile "
            + "can show it: the busiest traced operations (HTTP and gRPC routes, jobs) with count, errors and "
            + "p95 and, from the samples inside their spans, the methods of yours they entered through "
            + "(entryPoints - the handler) and spent most in (topMethods); the JDBC statements with the lines "
            + "of yours that issue them; the trace attribute keys with their cardinality; the notification "
            + "types raised; and the application's own JFR event types, with whether each records stacks. "
            + "Reconcile it with the source: routes never exercised, operations with no business attributes, "
            + "statements issued from a loop. Each section says whether it is read from event stacks or "
            + "inferred from samples.")
    @McpOutputSchema(RuntimeSurface.class)
    @McpToolMeta(cost = McpToolCost.EXPENSIVE)
    public McpToolResult runtimeSurface(
            @ToolParam(required = false, description = "Your own package prefixes, e.g. [\"com.acme.\"]. Omitted, "
                    + "the profile's busiest non-library packages are used.")
            List<String> framePrefixes) {

        ProfileInfo info = profileManager.info();
        List<String> given = CodeAnswers.prefixes(framePrefixes);
        Set<String> recorded = Set.copyOf(codes(profileManager.flamegraphManager().allEventSummaries()));
        Set<String> withStacks = Set.copyOf(codes(profileManager.flamegraphManager().graphableEventSummaries()));
        Optional<Type> sampled = REQUEST_SAMPLE_TYPES.stream()
                .filter(type -> withStacks.contains(type.code()))
                .findFirst();
        RelativeTimeRange whole = FlamegraphMcpTools.timeRange(info, null);

        List<String> suggested = given.isEmpty()
                ? suggestFrom(sampled.orElse(null), withStacks, whole)
                : List.of();
        List<String> prefixes = given.isEmpty() ? suggested : given;
        PrefixSource source = given.isEmpty() ? PrefixSource.SUGGESTED : PrefixSource.GIVEN;

        StatementsRead statementsRead = statements(withStacks, prefixes, whole);
        Statements statements = statementsRead.answer();
        OperationsRead operationsRead = operations(info, sampled, prefixes, whole, statementsRead.entries());
        Operations operations = operationsRead.answer();
        Optional<MethodRef> entryRef = operationsRead.firstEntryPoint().map(MethodRef::of);
        List<AttributeKey> keys = profileManager.traceAttributesManager().keys().stream()
                .sorted(Comparator.comparingInt((TraceAttributeKeyRow row) -> sourceRank(row.source()))
                        .thenComparing(Comparator.comparingLong(TraceAttributeKeyRow::traceCount).reversed())
                        .thenComparing(TraceAttributeKeyRow::key))
                .map(AttributeKey::of)
                .toList();
        List<TraceNotificationGroupRow> notificationRows = profileManager.traceManager()
                .notifications(TraceNotificationListQuery.all(MAX_NOTIFICATIONS));
        List<CustomEventType> custom = profileManager.flamegraphManager().allEventSummaries().stream()
                .filter(summary -> PLATFORM_PREFIXES.stream().noneMatch(summary.code()::startsWith))
                .map(summary -> new CustomEventType(summary.code(), summary.label(),
                        summary.primary() == null ? 0 : summary.primary().samples(), withStacks.contains(summary.code())))
                .toList();

        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(!statements.statements().isEmpty(), () -> NextCalls.to(CALL_SITES_TOOL)
                        .with(PROFILE_ID, info.id())
                        .with(EVENT_TYPE, statements.statements().getFirst().eventType())
                        .with(FRAME_PREFIXES, prefixes)
                        .why(STATEMENTS_WHY))
                .nextWhen(entryRef.isPresent(), () -> NextCalls.to(METHOD_TOOL)
                        .with(PROFILE_ID, info.id())
                        .with(CLASS_NAME, entryRef.orElseThrow().className())
                        .with(METHOD_NAME, entryRef.orElseThrow().methodName())
                        .with(FRAME_PREFIXES, prefixes)
                        .why(ENTRY_WHY))
                .guidanceWhen(source == PrefixSource.SUGGESTED, SUGGESTED_GUIDANCE)
                .followUp();

        List<AttributeKey> shownKeys = ToolArguments.firstOf(keys, MAX_ATTRIBUTE_KEYS);
        List<CustomEventType> shownCustom = ToolArguments.firstOf(custom, MAX_CUSTOM_TYPES);
        return McpToolResult.of(new RuntimeSurface(info.id(), prefixes, source, suggested, operations, statements,
                shownKeys, keys.size() - shownKeys.size(),
                notificationRows.stream().map(Notification::of).toList(),
                shownCustom, custom.size() - shownCustom.size(),
                followUp, UiLinks.view(info.id(), MicroscopeView.TRACES_OPERATIONS)));
    }

    private List<String> suggestFrom(Type sampled, Set<String> withStacks, RelativeTimeRange whole) {
        Type source = sampled != null ? sampled : JDBC_TYPES.stream()
                .filter(type -> withStacks.contains(type.code()))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return List.of();
        }
        CodeAttribution everything = profileManager.flamegraphManager().codeAttribution(
                CodeAnswers.params(source, whole, false, false, null), CodeQuery.of(List.of(), List.of()));
        return SuggestedPrefixes.from(everything.inclusiveByMethod());
    }

    private OperationsRead operations(
            ProfileInfo info, Optional<Type> sampled, List<String> prefixes, RelativeTimeRange whole,
            Map<TraceOperationId, List<OperationEntry>> entriesFromStatements) {
        TraceOperationsPage page = profileManager.traceManager()
                .operations(TraceOperationListQuery.busiest(MAX_OPERATIONS));
        if (page.operations().isEmpty()) {
            return new OperationsRead(
                    new Operations(SectionState.NOT_RECORDED, NO_OPERATIONS_NOTE, null, List.of(), 0), Optional.empty());
        }
        MethodKey firstEntry = null;
        boolean placeable = sampled.isPresent() && !prefixes.isEmpty();
        List<Operation> rows = new ArrayList<>();
        for (TraceOperationRow row : page.operations()) {
            List<FrameShare> entries = List.of();
            List<FrameShare> methods = List.of();
            Long samples = null;
            EntryBasis basis = EntryBasis.NONE;
            List<OperationEntry> recorded = entriesFromStatements.getOrDefault(
                    new TraceOperationId(row.name(), row.kind(), row.eventType()), List.of());
            if (!recorded.isEmpty()) {
                long events = recorded.stream().mapToLong(OperationEntry::events).sum();
                entries = recorded.stream()
                        .limit(MAX_CODE_PER_OPERATION)
                        .map(entryRow -> new FrameShare(entryRow.method().displayName(), entryRow.events(),
                                CodeAnswers.pct(entryRow.events(), events)))
                        .toList();
                basis = EntryBasis.EVENT_STACK;
                if (firstEntry == null) {
                    firstEntry = recorded.getFirst().method();
                }
            }
            if (placeable) {
                CodeAttribution inside = profileManager.flamegraphManager().codeAttribution(
                        CodeAnswers.params(sampled.get(), whole, false, false,
                                new SpanScope.Operation(row.name(), row.kind(), row.eventType())),
                        CodeQuery.of(prefixes, List.of()));
                samples = inside.chargedToOwnCode();
                if (basis == EntryBasis.NONE && !inside.entryPoints().isEmpty()) {
                    entries = CodeAnswers.frames(inside.entryPoints(), inside.chargedToOwnCode(), MAX_CODE_PER_OPERATION);
                    basis = EntryBasis.SAMPLED_SPANS;
                    if (firstEntry == null) {
                        firstEntry = inside.entryPoints().getFirst().method();
                    }
                }
                methods = inside.ownMethods().stream()
                        .filter(cost -> cost.charged() > 0)
                        .limit(MAX_CODE_PER_OPERATION)
                        .map(cost -> new FrameShare(cost.method().displayName(), cost.charged(),
                                CodeAnswers.pct(cost.charged(), inside.chargedToOwnCode())))
                        .toList();
            }
            rows.add(new Operation(row.name(), row.kind(), row.eventType(), row.count(), row.errorCount(),
                    row.p95Nanos(), samples, basis, entries, methods,
                    TraceLinks.operation(info.id(), row.name(), row.kind(), row.eventType(), null)));
        }
        String note = placeable
                ? ENTRY_INFERRED_NOTE.formatted(sampled.get().code())
                : NO_SAMPLES_NOTE;
        return new OperationsRead(
                new Operations(SectionState.READ, note, sampled.map(Type::code).orElse(null), rows,
                        (int) Math.max(0, page.totalMatching() - rows.size())),
                Optional.ofNullable(firstEntry));
    }

    /** The operations section, and the first handler it found, for the next call. */
    private record OperationsRead(Operations answer, Optional<MethodKey> firstEntryPoint) {
    }

    private StatementsRead statements(Set<String> withStacks, List<String> prefixes, RelativeTimeRange whole) {
        List<Type> types = JDBC_TYPES.stream().filter(type -> withStacks.contains(type.code())).toList();
        if (types.isEmpty()) {
            return new StatementsRead(
                    new Statements(SectionState.NOT_RECORDED, NO_STATEMENTS_NOTE, List.of(), 0), Map.of());
        }
        Map<TraceOperationId, Map<MethodKey, Long>> entries = new LinkedHashMap<>();
        FrameScope scope = FrameScope.of(prefixes);
        Map<StatementKey, StatementAccumulator> byStatement = new LinkedHashMap<>();
        for (Type type : types) {
            CallSites sites = profileManager.flamegraphManager().callSites(
                    CodeAnswers.params(type, whole, false, false, null),
                    new CallSiteQuery(scope, STATEMENT_NAME_FIELD, null, null));
            for (OperationEntry entry : sites.operationEntries()) {
                entries.computeIfAbsent(entry.operation(), _ -> new LinkedHashMap<>())
                        .merge(entry.method(), entry.events(), Long::sum);
            }
            for (CallSite site : sites.sites()) {
                String statement = site.groupValue() == null ? type.code() : site.groupValue();
                byStatement.computeIfAbsent(new StatementKey(type.code(), statement),
                        _ -> new StatementAccumulator(type.code(), statement)).add(site);
            }
        }
        List<Statement> all = byStatement.values().stream()
                .map(StatementAccumulator::toStatement)
                .sorted(Comparator.comparingLong(Statement::totalNanos).reversed()
                        .thenComparing(Statement::statement))
                .toList();
        List<Statement> shown = ToolArguments.firstOf(all, MAX_STATEMENTS);
        Map<TraceOperationId, List<OperationEntry>> byOperation = new LinkedHashMap<>();
        entries.forEach((operation, methods) -> byOperation.put(operation, methods.entrySet().stream()
                .map(method -> new OperationEntry(operation, method.getKey(), method.getValue()))
                .sorted(Comparator.comparingLong(OperationEntry::events).reversed()
                        .thenComparing(OperationEntry::method))
                .toList()));
        return new StatementsRead(
                new Statements(SectionState.READ, STATEMENTS_NOTE, shown, all.size() - shown.size()), byOperation);
    }

    /** The statements section, and the handlers their stacks name per operation. */
    private record StatementsRead(Statements answer, Map<TraceOperationId, List<OperationEntry>> entries) {
    }

    private static int sourceRank(String source) {
        int rank = KEY_SOURCE_ORDER.indexOf(source);
        return rank < 0 ? KEY_SOURCE_ORDER.size() : rank;
    }

    private static List<String> codes(List<EventSummaryResult> summaries) {
        return summaries.stream()
                .filter(summary -> summary.primary() == null || summary.primary().samples() > 0)
                .map(EventSummaryResult::code)
                .toList();
    }

    private record StatementKey(String eventType, String statement) {
    }

    /** A call site's own path as Class#method:line, deepest first; no line when the recording kept none. */
    static List<String> path(CallSite site) {
        return site.ownPath().stream()
                .map(frame -> frame.line() > 0
                        ? frame.method().displayName() + LINE_SEPARATOR + frame.line()
                        : frame.method().displayName())
                .toList();
    }

    /** One statement and every own line that issues it. */
    private static final class StatementAccumulator {

        private final String eventType;
        private final String statement;
        private final List<CallSite> sites = new ArrayList<>();

        private StatementAccumulator(String eventType, String statement) {
            this.eventType = eventType;
            this.statement = statement;
        }

        private void add(CallSite site) {
            sites.add(site);
        }

        private Statement toStatement() {
            long count = sites.stream().mapToLong(CallSite::count).sum();
            long total = sites.stream().mapToLong(CallSite::totalNanos).sum();
            List<SiteRef> refs = sites.stream()
                    .sorted(Comparator.comparingLong(CallSite::totalNanos).reversed())
                    .limit(MAX_SITES_PER_ROW)
                    .map(site -> new SiteRef(path(site), site.count(),
                            site.traces() > 0 && site.perTraceMedian() >= REPEATED_PER_TRACE))
                    .toList();
            return new Statement(eventType, statement, count, total, refs, Math.max(0, sites.size() - refs.size()));
        }
    }

    /**
     * Where events of one type were issued from in the reader's code.
     */
    record CallSitesAnswer(
            CodeStatus status,
            @McpNullable
            @McpDescription("Why there are no call sites; null when status is OK")
            String reason,
            String profileId,
            String eventType,
            @McpNullable
            @McpDescription("The window read; null for the whole recording")
            EpochWindow window,
            List<String> framePrefixes,
            PrefixSource framePrefixesSource,
            List<String> suggestedFramePrefixes,
            @McpNullable
            @McpDescription("The event field the call sites are split by; null for none")
            String groupBy,
            @McpNullable
            String filterField,
            @McpNullable
            String filterValue,
            @McpDescription("Every event read")
            long totalEvents,
            long totalNanos,
            @McpDescription("Events whose stack held no frame of your code")
            long unattributedEvents,
            @McpDescription("Call sites, longest total duration first")
            List<Site> sites,
            @McpDescription("Further call sites, counted rather than listed")
            int omittedSites,
            McpFollowUp followUp,
            @McpDescription("The flamegraph of the event type in Microscope, for the user")
            String uiLink) {

        static CallSitesAnswer empty(CodeStatus status, String reason, String profileId, Read read, List<String> prefixes,
                                     PrefixSource source, List<String> suggested, String group, String filterField,
                                     String filterValue, long totalEvents, long totalNanos, long unattributed,
                                     McpFollowUp followUp, String uiLink) {
            return new CallSitesAnswer(status, reason, profileId, read.type().code(), read.window(), prefixes, source,
                    suggested, group, filterField, filterValue, totalEvents, totalNanos, unattributed, List.of(), 0,
                    followUp, uiLink);
        }
    }

    /**
     * One line of your code and the events it issued.
     */
    record Site(
            @McpDescription("Class#method of the deepest frame of yours on the events' stacks")
            String callSite,
            @McpDescription(CodeAnswers.REFERENCE_DESCRIPTION)
            String reference,
            String className,
            String methodName,
            @McpNullable
            @McpDescription("The line the method was on when the events were committed; null when unknown")
            Integer line,
            @McpDescription("The deepest frames of yours on the events' stacks, deepest first, as Class#method:line - "
                    + "your own database or HTTP wrapper first, then the code that called it")
            List<String> ownPath,
            @McpNullable
            @McpDescription("The grouping field's value, cut to " + MAX_GROUP_VALUE_CHARS + " characters; null when "
                    + "not grouped or the field was absent")
            String groupValue,
            @McpNullable
            @McpDescription("The value's full length; null when there is none")
            Integer groupValueChars,
            long count,
            long totalNanos,
            long meanNanos,
            long maxNanos,
            @McpDescription("Distinct traces that issued it; 0 for events outside traces")
            int traces,
            @McpDescription("The most times one trace issued it")
            long perTraceMax,
            @McpDescription("The median times per trace, among the traces that issued it")
            double perTraceMedian,
            @McpDescription("Whether one request issues it at least " + REPEATED_PER_TRACE
                    + " times by the median: an N+1 shape")
            boolean repeatedPerTrace,
            @McpDescription("The traces that issued it most often")
            List<ExemplarTrace> exemplarTraces,
            @McpDescription("The flamegraph of the event type with this method searched, for the user")
            String uiLink) {

        static Site of(CallSite site, String profileId, Read read) {
            String value = site.groupValue();
            boolean cut = value != null && value.length() > MAX_GROUP_VALUE_CHARS;
            return new Site(site.method().displayName(), MethodRef.of(site.method()).displayName(),
                    site.method().className(), site.method().methodName(),
                    site.line() > 0 ? site.line() : null,
                    path(site),
                    cut ? value.substring(0, MAX_GROUP_VALUE_CHARS) : value,
                    value == null ? null : value.length(),
                    site.count(), site.totalNanos(), site.count() == 0 ? 0 : site.totalNanos() / site.count(),
                    site.maxNanos(), site.traces(), site.perTraceMax(), site.perTraceMedian(),
                    site.traces() > 0 && site.perTraceMedian() >= REPEATED_PER_TRACE,
                    site.exemplarTraceIds().stream()
                            .map(id -> new ExemplarTrace(TraceIds.hex(id), TraceLinks.trace(profileId, TraceIds.hex(id))))
                            .toList(),
                    read.graphLink(profileId, CodeAnswers.search(site.method())));
        }
    }

    record ExemplarTrace(
            @McpDescription("The trace id as traces_traceExport takes it")
            String traceId,
            @McpDescription("The trace's span waterfall in Microscope, for the user")
            String uiLink) {
    }

    /**
     * What ran, and where in the code.
     */
    record RuntimeSurface(
            String profileId,
            List<String> framePrefixes,
            PrefixSource framePrefixesSource,
            List<String> suggestedFramePrefixes,
            Operations operations,
            Statements statements,
            @McpDescription("Trace attribute keys, most traces first: the business dimensions the application "
                    + "records on its spans")
            List<AttributeKey> attributeKeys,
            int omittedAttributeKeys,
            @McpDescription("The notification types the application raised inside traces")
            List<Notification> notifications,
            @McpDescription("Event types that are neither the JDK's, Jeffrey's nor a profiler's: the application's own")
            List<CustomEventType> customEventTypes,
            int omittedCustomEventTypes,
            McpFollowUp followUp,
            @McpDescription("The traced operations in Microscope, for the user")
            String uiLink) {
    }

    /** Where an operation's entry points were read from. */
    enum EntryBasis {
        /** The stacks of the statements its requests issued: the handler is recorded, not inferred. */
        EVENT_STACK,
        /** The samples taken inside its spans: inferred from where samples landed. */
        SAMPLED_SPANS,
        /** Neither placed it: no statement with a stack, no sample of your code inside its spans. */
        NONE
    }

    record Operations(
            SectionState state,
            @McpNullable
            @McpDescription("How the section was read, or why it could not be")
            String note,
            @McpNullable
            @McpDescription("The sampled event type operations were placed in code with; null when none")
            String sampledEventType,
            @McpDescription("The busiest operations, by total time")
            List<Operation> operations,
            int omittedOperations) {
    }

    record Operation(
            String name,
            String kind,
            String eventType,
            long count,
            long errorCount,
            long p95Nanos,
            @McpNullable
            @McpDescription("Samples inside its spans that ran your code - what entryPoints and topMethods are "
                    + "shares of; a handful places the operation loosely. Null when it could not be placed")
            Long ownSamples,
            @McpDescription("How entryPoints were read: from the stacks of the statements its requests issued "
                    + "(EVENT_STACK, recorded), from samples inside its spans (SAMPLED_SPANS, inferred), or not at all")
            EntryBasis entryBasis,
            @McpDescription("The outermost methods of yours its requests entered through - the handler - with "
                    + "their share of the events or samples entryBasis names")
            List<FrameShare> entryPoints,
            @McpDescription("Your methods its samples were charged to most; share of its own samples")
            List<FrameShare> topMethods,
            @McpDescription("The operation in Microscope, for the user")
            String uiLink) {
    }

    record Statements(
            SectionState state,
            @McpNullable
            String note,
            @McpDescription("Statements, longest total duration first")
            List<Statement> statements,
            int omittedStatements) {
    }

    record Statement(
            String eventType,
            @McpDescription("The statement's name as recorded (verb and table, or Mapper.method)")
            String statement,
            long count,
            long totalNanos,
            @McpDescription("The lines of yours that issue it, longest first")
            List<SiteRef> callSites,
            int omittedCallSites) {
    }

    record SiteRef(
            @McpDescription("The frames of yours that issue it, deepest first, as Class#method:line")
            List<String> ownPath,
            long count,
            @McpDescription("Whether one request issues it at least " + REPEATED_PER_TRACE + " times by the median")
            boolean repeatedPerTrace) {
    }

    record AttributeKey(String key, String source, String valueKind, long distinctValues, long traceCount) {

        static AttributeKey of(TraceAttributeKeyRow row) {
            return new AttributeKey(row.key(), row.source(), row.valueKind(), row.distinctValues(), row.traceCount());
        }
    }

    record Notification(
            String type,
            String severity,
            @McpNullable
            String category,
            long count,
            long traceCount) {

        static Notification of(TraceNotificationGroupRow row) {
            return new Notification(row.type(), row.severity(), row.category(), row.count(), row.traceCount());
        }
    }

    record CustomEventType(
            String eventType,
            @McpNullable
            String label,
            long count,
            boolean withStacks) {
    }
}
