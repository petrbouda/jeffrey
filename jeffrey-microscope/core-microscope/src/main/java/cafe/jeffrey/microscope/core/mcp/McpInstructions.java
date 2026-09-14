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
package cafe.jeffrey.microscope.core.mcp;

/**
 * What a client is told at {@code initialize} about how to use this server.
 * <p>
 * A hundred-odd tools in nineteen families is a lot to meet with nothing but a tool list, and the
 * account of how to use them lives in the plugin's skills — which a Claude Code, Codex or Gemini user
 * installs and nobody else gets. This is the part of that account which is too important to leave
 * behind a {@code prompts/get}: where to start, the argument every tool wants, what the families are
 * for, and the two rules a reader gets wrong otherwise.
 * <p>
 * Deliberately short. It is sent on every handshake and costs context in every session, so it says
 * what a client cannot discover from {@code tools/list} and stops: the sequence, the rules, and where
 * the longer guidance is. Each skill remains a prompt, and {@code jeffrey://server} still reports what
 * this installation actually advertises.
 */
final class McpInstructions {

    private McpInstructions() {
    }

    static final String TEXT = """
            Jeffrey Microscope analyses JVM recordings: JFR profiles, async-profiler output, pprof/OTLP \
            and heap dumps. It reads recordings it already holds; it never profiles a running process.

            Start here. Call profiles_list to find a profile and take its profileId. Then call \
            profiles_summary, and read two of its fields before choosing a tool: topFindings, which is \
            what the rule set already flagged, and capabilityGaps, which is the questions this \
            recording cannot answer. Asking a family about something the recording never enabled is \
            the most common wasted call.

            Every tool except profiles_list and the recordings_, hubs_ and operations_ families takes \
            a required profileId. It is the id from profiles_list and nothing else works without one.

            Which family answers what: profiles_ the catalogue and what a profile can answer; \
            flamegraph_ call trees for one event type; compare_ two profiles against each other; \
            traces_ latency, slow operations and spans; jvm_ the machine underneath (GC, safepoints, \
            JIT, threads, native memory, container, flags); http_, jdbc_, grpc_, methodtracing_ the \
            technology dashboards; io_ and blocking_ time spent waiting rather than running; timeline_ \
            when rather than where; memory_ allocation and leak candidates without a heap dump; heap_ \
            heap dumps, their dominator tree and GC-root paths; jfr_ DuckDB SQL over the profile \
            database when no dashboard answers; recordings_ turning a file into a profile; hubs_ \
            recordings held on another machine; ide_ where a frame lives in the developer's editor; \
            operations_ the work the writers start.

            Nine tools are not read-only: recordings_analyzeFile, recordings_analyzeRecording, \
            heap_prepare, hubs_download, hubs_eventActivity, hubs_activityCancel, operations_cancel, \
            ide_link and ide_open. None of them alters an analysed profile. Each returns an \
            operationId rather than blocking; poll operations_status until it completes, and \
            operations_cancel to stop it.

            Two rules worth knowing before the first surprise. Output is capped at 120,000 characters \
            and always says when it cut, in a TRUNCATED line or a _truncated object — a short answer \
            that says nothing was cut is complete. And a heap dump must be indexed before most heap_ \
            tools answer: heap_prepare builds it, heap_status reports on it.

            Fuller guidance is served as prompts. prompts/list names ten, one per workflow — \
            analyze-jfr, analyze-heap, analyze-hub, compare-jfr, advise-jfr, profile-run, \
            regression-check, jfr-sql, heap-sql, and report, which is the evidence discipline the \
            others write to. Read the one that matches the question before a long investigation. \
            The jeffrey://server resource reports which families this installation actually advertises.\
            """;
}
