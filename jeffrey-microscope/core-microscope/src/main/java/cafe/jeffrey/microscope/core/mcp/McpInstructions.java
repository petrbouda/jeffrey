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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.BLOCKING;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.COMPARE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.FLAMEGRAPH;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.GRPC;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.HEAP;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.HTTP;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.HUBS;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.IDE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.IO;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JDBC;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JFR;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JVM;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.MEMORY;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.METHOD_TRACING;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.OPERATIONS;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.PROFILES;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.RECORDINGS;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.TIMELINE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.TRACES;

/**
 * What a client is told at {@code server/discover} about how to use this server.
 * <p>
 * A hundred-odd tools in nineteen families is a lot to meet with nothing but a tool list, and the
 * account of how to use them lives in the plugin's skills — which a plugin user installs and nobody
 * else gets. This is the part of that account which is too important to leave behind a
 * {@code prompts/get}: where to start, the argument every tool wants, what the families are for, and
 * the two rules a reader gets wrong otherwise.
 * <p>
 * Deliberately short. It is sent on every {@code server/discover} and costs context in every session,
 * so it says what a client cannot discover from {@code tools/list} and stops: the sequence, the rules,
 * and where the longer guidance is. Each skill remains a prompt and a skill of the skills extension, and
 * {@code jeffrey://server} still reports what this installation actually advertises.
 * <p>
 * This is the one place on the server where call order lives. A tool description says what the tool
 * does, what it returns and what it is not for; "call X first" belongs here, in the family's own
 * {@link OrderRule}, because a host reads descriptions to choose a tool and cuts a long one off.
 * <p>
 * Written for the families this installation advertises and no others: a paragraph about a family
 * that is withheld would send the reader after tools that do not exist, and the writer count is the
 * count of the writers that are actually served.
 */
final class McpInstructions {

    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final String LIST_SEPARATOR = ", ";
    private static final String LAST_SEPARATOR = " and ";
    /** Joins the family guides into one sentence: prose, not a tool name, whatever it separates. */
    private static final String PROSE_JOINER = "; ";

    private static final String INTRODUCTION = """
            Jeffrey Microscope analyses JVM recordings: JFR profiles, async-profiler output, pprof/OTLP \
            and heap dumps. It reads recordings it already holds; it never profiles a running process.""";

    private static final String START_HERE = """
            Start here. Call profiles_list to find a profile and take its profileId. Then call \
            profiles_summary, and read two of its fields before choosing a tool: topFindings, which is \
            what the rule set already flagged, and capabilityGaps, which is the questions this \
            recording cannot answer. Asking a family about something the recording never enabled is \
            the most common wasted call. When the question is open, investigationAreas is the menu to \
            put to the user - each area the profile can answer, its weight and what suggests it - and \
            the user picks what is worth running.""";

    private static final String PROFILE_ID_ALL = "Every tool takes a required profileId.";
    private static final String PROFILE_ID_EXCEPT = "Every tool except %s takes a required profileId.";
    private static final String PROFILE_ID_SOURCE = " It is the id from profiles_list and nothing else works without one.";
    private static final String PROFILES_LIST_TOOL = "profiles_list";
    private static final String FAMILY_PREFIX = "the ";
    private static final String ONE_FAMILY_SUFFIX = " family";
    private static final String FAMILIES_SUFFIX = " families";

    /** The families whose tools take no profileId, in the order the sentence names them. */
    private static final List<String> UNSCOPED_FAMILIES = List.of(RECORDINGS, HUBS, OPERATIONS);

    private static final String FAMILY_GUIDE = "Which family answers what: %s.";

    /** Each family and what it answers, in the order a reader meets them. */
    private static final List<FamilyGuide> FAMILY_GUIDES = List.of(
            new FamilyGuide(PROFILES, "profiles_ the catalogue and what a profile can answer"),
            new FamilyGuide(FLAMEGRAPH, "flamegraph_ call trees for one event type"),
            new FamilyGuide(COMPARE, "compare_ two profiles against each other"),
            new FamilyGuide(TRACES, "traces_ latency, slow operations and spans"),
            new FamilyGuide(JVM, "jvm_ the machine underneath (GC, safepoints, JIT, threads, native "
                    + "memory, container, flags)"),
            new FamilyGuide(HTTP, "http_ the HTTP traffic dashboard"),
            new FamilyGuide(JDBC, "jdbc_ the database statement and connection-pool dashboard"),
            new FamilyGuide(GRPC, "grpc_ the gRPC traffic dashboard"),
            new FamilyGuide(METHOD_TRACING, "methodtracing_ the traced-method dashboard"),
            new FamilyGuide(IO, "io_ time spent waiting on files and sockets rather than running"),
            new FamilyGuide(BLOCKING, "blocking_ time spent waiting on locks, parks and sleeps rather "
                    + "than running"),
            new FamilyGuide(TIMELINE, "timeline_ when rather than where"),
            new FamilyGuide(MEMORY, "memory_ allocation and leak candidates without a heap dump"),
            new FamilyGuide(HEAP, "heap_ heap dumps, their dominator tree and GC-root paths"),
            new FamilyGuide(JFR, "jfr_ DuckDB SQL over the profile database when no dashboard answers"),
            new FamilyGuide(RECORDINGS, "recordings_ turning a file into a profile"),
            new FamilyGuide(HUBS, "hubs_ recordings and the files beside them held on another machine"),
            new FamilyGuide(IDE, "ide_ where a frame lives in the developer's editor"),
            new FamilyGuide(OPERATIONS, "operations_ the work the writers start"));

    private static final String ORDER_INTRODUCTION = "Call order that matters:";
    private static final String SENTENCE_SEPARATOR = " ";

    /**
     * The order each family is used in, gated on every family a rule names so that it never points at a
     * tool this installation withholds.
     */
    private static final List<OrderRule> ORDER_RULES = List.of(
            new OrderRule(Set.of(PROFILES), "profiles_summary carries everything profiles_features does; a "
                    + "negative answer to a question its capabilityGaps lists is not a clean one. "
                    + "profiles_samplerHealth is worth one call before shares are reported as fact, "
                    + "profiles_get's source commit is compared with the checkout before frames are mapped "
                    + "to code, and profiles_viewLink ends an explanation - the reader opens it, it is never "
                    + "read back."),
            new OrderRule(Set.of(FLAMEGRAPH), "flamegraph_list gives the eventType values flamegraph_export "
                    + "accepts; call it before the first export."),
            new OrderRule(Set.of(TIMELINE), "Before a flamegraph of anything bursty, timeline_hotWindows "
                    + "finds the window whose startEpochMs and endEpochMs make the export show the spike; "
                    + "timeline_zoom looks inside it or at a startup."),
            new OrderRule(Set.of(COMPARE), "compare_list comes before any other compare_ tool and "
                    + "compare_quality before a comparison is interpreted; the comparability section that "
                    + "opens compare_movements is read before any finding is reported, and compare_flamegraph "
                    + "follows a method compare_movements flagged."),
            new OrderRule(Set.of(TRACES), "traces_overview first, to see whether the profile has traces at "
                    + "all, and traces_notifications before any timing is read; traces_operations, "
                    + "traces_slowestTraces and traces_operationExport lead to the ids traces_traceExport "
                    + "takes. Among the attribute tools traces_attributeKeys comes first, then "
                    + "traces_attributeValues to pick the value, then traces_attributeSearch."),
            new OrderRule(Set.of(JVM), "jvm_sections comes before the other jvm_ tools, and "
                    + "jvm_autoAnalysis is the cheapest first question. jvm_gcDetail follows jvm_gc once it "
                    + "has shown collection matters and named the collector, jvm_threadDump follows "
                    + "jvm_threadDumps once it has named the dump, and jvm_configuration and jvm_flags are "
                    + "read before any flag is proposed."),
            new OrderRule(Set.of(JFR), "Before SQL: jfr_listEventTypes names the event types, "
                    + "jfr_describeEventType the fields inside one you have not queried before, and "
                    + "jfr_listTables and jfr_describeTable the schema jfr_executeQuery runs against."),
            new OrderRule(Set.of(HEAP), "heap_getDumpMetadata and heap_getHeapSummary open a heap "
                    + "investigation. heap_prepare is called once when a report answers NOT_RUN_YET or a "
                    + "ranking by retained size comes back empty, and heap_status is polled after it rather "
                    + "than the report retried. heap_listTables and heap_describeTable come before "
                    + "heap_executeQuery, and heap_diff takes the earlier dump as its baseline."),
            new OrderRule(Set.of(RECORDINGS), "A recordings_analyzeFile or recordings_analyzeRecording that "
                    + "answered with a running operationId or a task is followed, not called again; "
                    + "recordings_delete clears a recording once its question is answered."),
            new OrderRule(Set.of(RECORDINGS, PROFILES), "After a recordings_delete, profiles_list is the "
                    + "catalogue to trust: the deleted profile id no longer works."),
            new OrderRule(Set.of(HUBS, RECORDINGS), "A question about an environment rather than a file starts at "
                    + "hubs_sessions, with hubs_list when it shows nothing from a hub you expected. "
                    + "hubs_download brings the session, a window named by its window argument (the last "
                    + "minutes, the startup, latest or peak chunk, the minutes before or around a moment) or "
                    + "the span its started and duration columns allow, and recordings_analyzeRecording turns "
                    + "the recordingId into a profile; a transfer still running is joined by calling "
                    + "hubs_download with the arguments its followUp names, or by following its task."),
            new OrderRule(Set.of(IDE), "ide_resolve comes before a finding names a file or a line - the "
                    + "exports carry call paths, never file paths; ide_windows settles a window ide_resolve "
                    + "calls ambiguous and ide_link pins it; ide_open is for when the reader asked to be "
                    + "shown something, never while gathering evidence."));

    private static final String DASHBOARDS = "Each dashboard family starts at its overview (%s), which "
            + "names the endpoint, statement group, service, method or target its detail tools take.";

    /** The dashboard families and their overview tools, in the order the sentence names them. */
    private static final List<Dashboard> DASHBOARDS_BY_FAMILY = List.of(
            new Dashboard(HTTP, "http_overview"),
            new Dashboard(JDBC, "jdbc_overview"),
            new Dashboard(GRPC, "grpc_overview"),
            new Dashboard(METHOD_TRACING, "methodtracing_overview"),
            new Dashboard(IO, "io_overview"));

    private static final String ARTIFACTS = """
            A JVM writes more than it records. When the question is about an application log, a GC \
            log or why a JVM died, call hubs_files on the session, hubs_fetchFile on the file, and \
            then open the path it returns with your own tools - Jeffrey runs on this machine and \
            hands you the file rather than parsing it for you. hubs_files also shows the path of a \
            file already here, and the profile whose timeline its timestamps line up with.""";

    /** Every tool that is not read-only, in the order the sentence names them. */
    private static final List<Writer> WRITERS = List.of(
            Writer.slow(RECORDINGS, "recordings_analyzeFile", "recordings_analyzeFile"),
            Writer.slow(RECORDINGS, "recordings_analyzeRecording", "recordings_analyzeRecording"),
            Writer.quick(RECORDINGS, "recordings_delete"),
            Writer.slow(HEAP, "heap_prepare", "heap_prepare"),
            Writer.slow(HEAP, "heap_oql", "heap_oql with includeRetainedSize"),
            Writer.slow(HUBS, "hubs_download", "hubs_download"),
            Writer.slow(HUBS, "hubs_fetchFile", "hubs_fetchFile"),
            Writer.slow(JVM, "jvm_autoAnalysis", "jvm_autoAnalysis with compute"),
            Writer.quick(OPERATIONS, "operations_cancel"),
            Writer.quick(IDE, "ide_link"),
            Writer.quick(IDE, "ide_open"));

    private static final String ALL_READ_ONLY = "Every tool here is read-only.";
    private static final String WRITERS_ONE = "%s tool is not read-only: %s. It does not alter an analysed profile.";
    private static final String WRITERS_MANY = "%s tools are not read-only: %s. None of them alters an analysed profile.";
    /**
     * The other way to follow the same work. One text serves every client, so it names both paths; every
     * slow writer's family starts an operation, and a served operation is what declares the extension.
     */
    private static final String TASK_PATH = " A client that declared the MCP tasks extension gets a standard "
            + "task after about 5 s instead, whose taskId is that operationId: follow it with tasks/get and "
            + "stop it with tasks/cancel.%s The rest answer straight away and have no operationId to poll.";
    private static final String SLOW_ONE = " The one that can take a while - %s - waits up to 45 s, then "
            + "answers with an operationId; poll operations_status until it completes, and "
            + "operations_cancel to stop it." + TASK_PATH;
    private static final String SLOW_MANY = " The %s that can take a while - %s - wait up to 45 s, then "
            + "answer with an operationId; poll operations_status until it completes, and "
            + "operations_cancel to stop it." + TASK_PATH;
    private static final String NONE_SLOW = " They all answer straight away and have no operationId to poll.";
    /** Which handle comes back is the client's declaration, not the server's choice; neither waits. */
    private static final String HEAP_PREPARE_AT_ONCE = " heap_prepare does not wait: a client that declared the "
            + "tasks extension gets its task at once, any other client its operationId.";
    private static final String NO_NOTE = "";

    private static final Map<Integer, String> NUMBER_WORDS = Map.ofEntries(
            Map.entry(1, "one"), Map.entry(2, "two"), Map.entry(3, "three"), Map.entry(4, "four"),
            Map.entry(5, "five"), Map.entry(6, "six"), Map.entry(7, "seven"), Map.entry(8, "eight"),
            Map.entry(9, "nine"), Map.entry(10, "ten"), Map.entry(11, "eleven"));

    /**
     * How every answer reads. Stated once here rather than rediscovered from a hundred schemas, because
     * an agent that misreads one of these misreads every answer: a window passed on as an offset, a
     * status retried as an error, a cursor rebuilt by hand, a link fetched instead of handed over.
     */
    private static final String RESULTS = """
            How an answer reads: each tool's structuredContent is a typed record, valid against its \
            outputSchema. Times are UTC epoch milliseconds (…EpochMs) - pass a window from one answer \
            straight on as startEpochMs and endEpochMs - and durations carry their unit (…Ms, …Nanos). \
            Enum values are upper case, in answers and arguments alike. A question with no data \
            answers with a status such as NOT_RECORDED and a reason, not an error; an unknown id is an \
            error naming it. A list that continues returns hasMore and nextCursor: pass nextCursor back \
            as cursor with the same arguments. followUp.nextTools are the next calls, with this \
            answer's ids, windows and cursors already filled in, each with its weight - LIGHT, MEDIUM \
            or HEAVY, how much its answer puts into the conversation; followUp.guidance is advice that \
            is not a call. uiLink opens the same thing in Microscope for the user: give it to them with \
            your answer, and never fetch it.""";

    private static final String OUTPUT_CAP = """
            Worth knowing before the first surprise: output is capped at 120,000 characters and \
            always says when it cut - a structured answer in its own fields (truncated, an omitted… \
            count, hasMore), a Markdown one in a TRUNCATED line - so an answer that says nothing was \
            cut is complete.""";

    /**
     * What the hints every tool carries in its {@code _meta} mean, with examples drawn only from what the
     * advertised tools carry: SLOW is named only when a served writer is slow, and jeffrey/requires only
     * when a served family declares a requirement, by that family's own.
     */
    private static final String HINTS_COST_ONLY = " Each tool's _meta says what a call costs: jeffrey/cost is %s.";
    private static final String HINTS_COST_AND_NEEDS = " Each tool's _meta says what a call costs and needs: "
            + "jeffrey/cost is %s, and jeffrey/requires, when present, lists what must already be in place, "
            + "such as %s.";
    private static final String COST_SCALE = "CHEAP, MODERATE or EXPENSIVE";
    private static final String COST_SCALE_WITH_SLOW = "CHEAP, MODERATE, EXPENSIVE, or "
            + McpToolCost.SLOW.name() + " for one that may finish its work in the background";
    private static final String EXAMPLE_SEPARATOR = " or ";

    /** How many requirement examples the sentence gives at most. */
    private static final int MAX_REQUIREMENT_EXAMPLES = 2;

    /**
     * One requirement each family's tools declare, in the order the sentence offers them. A family
     * missing here declares none.
     */
    private static final List<RequirementExample> REQUIREMENT_EXAMPLES = List.of(
            new RequirementExample(TRACES, McpToolRequirement.TRACES),
            new RequirementExample(HEAP, McpToolRequirement.HEAP_DUMP_INDEXED),
            new RequirementExample(JVM, McpToolRequirement.AUTO_ANALYSIS),
            new RequirementExample(HUBS, McpToolRequirement.HUB),
            new RequirementExample(IDE, McpToolRequirement.IDE_LINKED));

    private static final String HEAP_INDEX = " And a heap dump must be indexed before most heap_ tools "
            + "answer: heap_prepare builds it, heap_status reports on it.";

    private static final String PROMPTS = """
            Fuller guidance is served as prompts and as skills. prompts/list names ten, one per \
            workflow — analyze-jfr, analyze-heap, analyze-hub, compare-jfr, advise-jfr, profile-run, \
            regression-check, jfr-sql, heap-sql, and report, which is the evidence discipline the \
            others write to. skills/list (the io.modelcontextprotocol/skills extension) serves the \
            same ten to any client that asks, and their files are read with resources/read. Read the \
            one that matches the question before a long investigation. \
            The jeffrey://server resource reports which families this installation actually advertises.""";

    private McpInstructions() {
    }

    /**
     * The instructions for an installation that advertises these families.
     */
    static String text(AdvertisedFamilies advertised) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(INTRODUCTION);
        if (advertised.has(PROFILES)) {
            paragraphs.add(START_HERE);
        }
        paragraphs.add(profileIdRule(advertised));
        paragraphs.add(familyGuide(advertised));
        callOrder(advertised).ifPresent(paragraphs::add);
        if (advertised.has(HUBS)) {
            paragraphs.add(ARTIFACTS);
        }
        paragraphs.add(writers(advertised));
        paragraphs.add(RESULTS);
        paragraphs.add(OUTPUT_CAP + hints(advertised) + (advertised.has(HEAP) ? HEAP_INDEX : NO_NOTE));
        paragraphs.add(PROMPTS);
        return String.join(PARAGRAPH_BREAK, paragraphs);
    }

    private static String profileIdRule(AdvertisedFamilies advertised) {
        List<String> exceptions = new ArrayList<>();
        if (advertised.has(PROFILES)) {
            exceptions.add(PROFILES_LIST_TOOL);
        }
        List<String> families = UNSCOPED_FAMILIES.stream()
                .filter(advertised::has)
                .map(family -> family + "_")
                .toList();
        if (!families.isEmpty()) {
            exceptions.add(FAMILY_PREFIX + joined(families)
                    + (families.size() == 1 ? ONE_FAMILY_SUFFIX : FAMILIES_SUFFIX));
        }
        String rule = exceptions.isEmpty()
                ? PROFILE_ID_ALL
                : PROFILE_ID_EXCEPT.formatted(joined(exceptions));
        return advertised.has(PROFILES) ? rule + PROFILE_ID_SOURCE : rule;
    }

    private static Optional<String> callOrder(AdvertisedFamilies advertised) {
        List<String> sentences = new ArrayList<>();
        ORDER_RULES.stream()
                .filter(rule -> rule.families().stream().allMatch(advertised::has))
                .map(OrderRule::text)
                .forEach(sentences::add);
        List<String> overviews = DASHBOARDS_BY_FAMILY.stream()
                .filter(dashboard -> advertised.has(dashboard.family()))
                .map(Dashboard::overview)
                .toList();
        if (!overviews.isEmpty()) {
            sentences.add(DASHBOARDS.formatted(String.join(LIST_SEPARATOR, overviews)));
        }
        if (sentences.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ORDER_INTRODUCTION + SENTENCE_SEPARATOR + String.join(SENTENCE_SEPARATOR, sentences));
    }

    private static String familyGuide(AdvertisedFamilies advertised) {
        List<String> guides = FAMILY_GUIDES.stream()
                .filter(guide -> advertised.has(guide.family()))
                .map(FamilyGuide::text)
                .toList();
        return FAMILY_GUIDE.formatted(String.join(PROSE_JOINER, guides));
    }

    private static String writers(AdvertisedFamilies advertised) {
        List<Writer> served = WRITERS.stream().filter(writer -> advertised.has(writer.family())).toList();
        if (served.isEmpty()) {
            return ALL_READ_ONLY;
        }
        List<String> names = served.stream().map(Writer::name).toList();
        String count = capitalised(NUMBER_WORDS.get(served.size()));
        String writers = (served.size() == 1 ? WRITERS_ONE : WRITERS_MANY).formatted(count, joined(names));

        List<String> slow = served.stream().filter(Writer::slow).map(Writer::slowDescription).toList();
        if (slow.isEmpty()) {
            return writers + NONE_SLOW;
        }
        String note = advertised.has(HEAP) ? HEAP_PREPARE_AT_ONCE : NO_NOTE;
        return writers + (slow.size() == 1
                ? SLOW_ONE.formatted(slow.getFirst(), note)
                : SLOW_MANY.formatted(NUMBER_WORDS.get(slow.size()), joined(slow), note));
    }

    private static String hints(AdvertisedFamilies advertised) {
        boolean slow = WRITERS.stream().anyMatch(writer -> writer.slow() && advertised.has(writer.family()));
        String scale = slow ? COST_SCALE_WITH_SLOW : COST_SCALE;
        List<String> examples = REQUIREMENT_EXAMPLES.stream()
                .filter(example -> advertised.has(example.family()))
                .limit(MAX_REQUIREMENT_EXAMPLES)
                .map(example -> example.requirement().name())
                .toList();
        if (examples.isEmpty()) {
            return HINTS_COST_ONLY.formatted(scale);
        }
        return HINTS_COST_AND_NEEDS.formatted(scale, String.join(EXAMPLE_SEPARATOR, examples));
    }

    /** "a", "a and b", "a, b and c". */
    private static String joined(List<String> items) {
        if (items.size() == 1) {
            return items.getFirst();
        }
        return String.join(LIST_SEPARATOR, items.subList(0, items.size() - 1)) + LAST_SEPARATOR + items.getLast();
    }

    private static String capitalised(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private record FamilyGuide(String family, String text) {
    }

    /** A requirement the tools of one family declare, offered as an example when it is advertised. */
    private record RequirementExample(String family, McpToolRequirement requirement) {
    }

    /** A dashboard family and the overview tool its detail tools are reached from. */
    private record Dashboard(String family, String overview) {
    }

    /**
     * @param families every family the rule names a tool of; the rule is left out unless all are advertised
     * @param text     the rule, as one or more sentences
     */
    private record OrderRule(Set<String> families, String text) {

        OrderRule {
            if (families.isEmpty()) {
                throw new IllegalArgumentException("An order rule belongs to at least one family");
            }
        }
    }

    /**
     * @param slowDescription how the sentence about long-running work names it, or null when it
     *                        always answers straight away
     */
    private record Writer(String family, String name, String slowDescription) {

        static Writer slow(String family, String name, String slowDescription) {
            return new Writer(family, name, slowDescription);
        }

        static Writer quick(String family, String name) {
            return new Writer(family, name, null);
        }

        boolean slow() {
            return slowDescription != null;
        }
    }
}
