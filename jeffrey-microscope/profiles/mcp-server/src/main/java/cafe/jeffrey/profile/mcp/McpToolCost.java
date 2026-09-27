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
package cafe.jeffrey.profile.mcp;

/**
 * What one call of a tool costs, in the kind of work it does rather than in milliseconds — advertised
 * as {@code _meta["jeffrey/cost"]}, by the constant's name, so an agent can choose the cheaper of two
 * tools that answer the same question and knows which call to make only once.
 * <p>
 * A scale, not a measurement: the same tool is faster on a small recording than on a large one, and
 * the figures {@link McpToolMetrics} keeps are what a call actually took on this installation.
 */
public enum McpToolCost {

    /**
     * An indexed lookup or a small aggregate: the catalogue, a profile's stored totals, a report or an
     * analysis already cached, one object or one dump read by its id, the state of an operation — or one
     * bounded call to one hub or to the IDE, answered within its deadline.
     */
    CHEAP,

    /**
     * A scan of one event type, or one table of a heap dump, into a dashboard, or a flamegraph at the
     * default threshold — or calls to several hubs, one per hub.
     */
    MODERATE,

    /**
     * A scan that can take seconds — caller-written SQL, a walk of the object graph, the same work done
     * over two profiles — a large export, or retained sizes, which may build the dominator tree first.
     */
    EXPENSIVE,

    /**
     * Can hand back an operation or a task instead of the answer: the call may outlast its wait, and
     * the work then continues in the background under an {@code operationId}.
     */
    SLOW
}
