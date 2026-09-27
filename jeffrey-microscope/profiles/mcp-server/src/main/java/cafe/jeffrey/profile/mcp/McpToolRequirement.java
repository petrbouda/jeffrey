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
 * Something that must already be in place before a tool can give its real answer — advertised as
 * {@code _meta["jeffrey/requires"]}, by the constants' names, sorted. A tool called without it does not
 * fail: it answers with a status or a refusal saying what is missing, and, where a call can provide it,
 * which one. The hint lets an agent see the precondition before the call instead of learning it from the
 * refusal — and make that call first when there is one.
 * <p>
 * Each constant is a precondition the code actually checks; none is a guess about what a recording
 * probably holds. What a recording captured at all — the event types behind a dashboard — is a
 * different question, answered by {@code profiles_summary}'s capability gaps.
 * <p>
 * A probe does not declare the requirement it probes. {@code traces_overview}, {@code hubs_list} and
 * {@code ide_windows} exist to find out whether traces, a hub or an IDE window are there, and answer
 * either way; declaring the requirement on them would tell an agent not to ask the question they answer.
 */
public enum McpToolRequirement {

    /**
     * The profile is a heap dump, whether or not it has been indexed. A JFR recording is refused.
     */
    HEAP_DUMP,

    /**
     * The profile's heap dump is indexed. A dump not yet indexed is refused, naming
     * {@code heap_prepare}, which builds the index.
     */
    HEAP_DUMP_INDEXED,

    /**
     * The cached heap-dump report the tool reads has been computed. Until then the answer is
     * {@code NOT_RUN_YET}, naming the {@code heap_prepare} report that computes it.
     */
    HEAP_REPORTS,

    /**
     * The profile's Auto Analysis is computed — at import, normally. Until then the answer is
     * {@code NOT_COMPUTED}, unless the call asks to compute it.
     */
    AUTO_ANALYSIS,

    /**
     * The recording carries traces from the Jeffrey tracing instrumentation. Without them the tool has
     * nothing to answer about; {@code traces_operations} and {@code traces_notifications} say so
     * explicitly.
     */
    TRACES,

    /**
     * An IntelliJ window with the Jeffrey plugin is answering on this machine. Without one the tool is
     * refused, since there is no window to act on.
     */
    IDE_RUNNING,

    /**
     * An IntelliJ window is linked to the profile, or exactly one window can be: the first lookup links
     * a single candidate itself, and otherwise answers with the candidates for {@code ide_link}.
     */
    IDE_LINKED,

    /**
     * This installation is connected to at least one Jeffrey Hub. Without one the answer says so.
     */
    HUB
}
