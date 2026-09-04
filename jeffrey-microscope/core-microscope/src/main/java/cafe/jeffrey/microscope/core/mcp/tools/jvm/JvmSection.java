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

import cafe.jeffrey.shared.common.model.Type;

import java.util.List;
import java.util.Set;

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
 * refused with a sentence instead of returning a page of zeroes, and lets {@code jvm_sections}
 * advertise the same availability without rendering anything.
 */
public sealed interface JvmSection permits
        AutoAnalysisSection,
        ConfigurationSection,
        ContainerSection,
        GcSection,
        JitSection,
        NativeMemorySection,
        SafepointsSection,
        ThreadsSection {

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
     * What this dashboard cannot answer, and which tool answers it — carried back with every result.
     * <p>
     * The figures alone do not say what to do next, and the tool description that does say it was
     * read many turns earlier. Jeffrey's flamegraph and trace exports have always opened with their
     * own reading instructions for exactly this reason; a dashboard is no different. These lines
     * route, they never diagnose: no threshold decides whether they appear, and none of them claims
     * that this particular recording is bad. The reader is told where the next answer lives and left
     * to decide whether to go there.
     */
    List<String> nextSteps();

    /**
     * The dashboard itself, as a record tree that serialises to JSON.
     */
    Object render();
}
