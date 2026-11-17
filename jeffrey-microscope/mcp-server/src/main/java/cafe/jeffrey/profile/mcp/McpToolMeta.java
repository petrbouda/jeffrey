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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a host should know about one tool before it calls it, carried in the tool's {@code _meta} in
 * {@code tools/list}. Every Jeffrey tool declares it, for its {@link #cost()} at least.
 * <p>
 * Claude Code warns at ten thousand tokens of tool result and, past twenty-five thousand by default,
 * writes the result to a file and hands the model a path instead. A flamegraph tree, a trace export or
 * a query result can land in that band on an ordinary profile, and read back from a file it costs the
 * model a second round trip for the answer it asked for. A tool that can answer that large declares it
 * here, and the host raises its limit for that tool alone rather than for the whole server.
 * <p>
 * Beside it, what a call costs and what must already be in place: an agent choosing between two tools
 * that answer the same question picks the cheaper one, and makes the call a precondition needs before
 * the one that needs it rather than after its refusal.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpToolMeta {

    /** The {@link #maxResultSizeChars()} of a tool that declares no size of its own. */
    int NOT_DECLARED = 0;

    /**
     * The largest result, in characters, the host should keep inline — advertised as
     * {@code anthropic/maxResultSizeChars}. Never above {@link McpToolOutput#MAX_CHARS}: the envelope
     * cuts every text result there, so a larger figure would promise text the host is never sent.
     * {@link #NOT_DECLARED} leaves the host's own default in place and advertises nothing.
     */
    int maxResultSizeChars() default NOT_DECLARED;

    /** What one call costs — advertised as {@code jeffrey/cost}. */
    McpToolCost cost();

    /**
     * What must already be in place — advertised as {@code jeffrey/requires}, sorted by name, and left
     * out when there is nothing. Each requirement is named once.
     */
    McpToolRequirement[] requires() default {};
}
