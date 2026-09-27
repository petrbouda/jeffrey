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
import cafe.jeffrey.microscope.model.Type;

import java.util.Set;
import java.util.function.Predicate;

/**
 * One machine-level dashboard, in the shape the Jeffrey UI already computes it.
 * <p>
 * These are the subsystems no other MCP family covers — garbage collection, safepoints, JIT
 * compilation, the container, native memory, threads and the JVM's own configuration. Each of them is
 * answerable from the profile database, but only through queries a model has to invent, and several
 * of those queries are ones it reliably gets wrong: pause time is {@code sumOfPauses} rather than an
 * event's duration, {@code jdk.GCHeapSummary} is two rows per collection, {@code jdk.SafepointLatency}
 * fires once per thread per safepoint. A section renders the manager the UI page renders, so the
 * numbers come from the same tested builders and cost one tool call instead of six round trips.
 * <p>
 * A section declares {@link #eventTypes()} — the types whose presence makes it answerable at all.
 * {@link JvmSections} tests that against what the recording holds, which is what lets a section be
 * answered with {@link SectionStatus#NOT_RECORDED} instead of a page of zeroes, and lets
 * {@code jvm_sections} advertise the same availability without rendering anything.
 * <p>
 * {@code D} is the section's own dashboard record, and the tool that serves the section publishes it
 * inside the section's answer record, so every dashboard reaches the wire typed and schema'd.
 *
 * @param <D> the dashboard this section renders
 */
public sealed interface JvmSection<D extends Record> permits
        AutoAnalysisSection,
        ClassLoadingSection,
        ConfigurationSection,
        ContainerSection,
        ExceptionsSection,
        GcSection,
        GcDetailSection,
        JitSection,
        NativeMemorySection,
        SafepointsSection,
        SecuritySection,
        SystemSection,
        ThreadsSection {

    /** The prefix every tool of the family carries; a section's tool is this and its id. */
    String TOOL_PREFIX = "jvm_";

    /**
     * The identifier the tool methods and {@code jvm_sections} share, lower camel case.
     */
    String id();

    /**
     * What the section is called for a reader — the UI's own name for the page.
     */
    String title();

    /**
     * The event types that make this section answerable. A recording carrying none of them cannot
     * produce the dashboard, and the section is reported as not recorded rather than rendered.
     * <p>
     * An empty set means the section does not depend on the recording's contents.
     */
    Set<Type> eventTypes();

    /**
     * The Microscope page the dashboard is drawn on, which every answer links for the user — on a
     * {@link SectionStatus#NOT_RECORDED} answer too, since the page exists either way.
     */
    MicroscopeView view();

    /**
     * The page a rendered dashboard is best seen on; the section's own page unless what it holds is
     * drawn on a more specific one.
     */
    default MicroscopeView view(D dashboard) {
        return view();
    }

    /**
     * What this dashboard cannot answer, and which call answers it — carried back with every
     * rendered result as the answer's {@code followUp}.
     * <p>
     * The figures alone do not say what to do next, and the tool description that does say it was
     * read many turns earlier. These entries route, they never diagnose: a call is added because the
     * thing it explains is present in the dashboard, never because a figure crossed a threshold. A
     * call to a tool this installation does not serve is left out by the builder.
     *
     * @param dashboard what {@link #render()} produced, so a call can carry this answer's own values
     */
    void followUp(NextSteps.Builder next, D dashboard);

    /**
     * The dashboard itself, as a record tree that serialises to JSON.
     */
    D render();

    /**
     * The dashboard as it is handed out, with every finding's next call that this installation cannot
     * serve left out. Only a dashboard that carries findings has anything to drop.
     */
    default D reachable(D dashboard, Predicate<String> servesTool) {
        return dashboard;
    }

    /** The tool that serves this section. */
    default String tool() {
        return TOOL_PREFIX + id();
    }
}
