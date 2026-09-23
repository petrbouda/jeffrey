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
 * Answers {@code completion/complete}: what a client may offer for one argument of a resource template
 * or a prompt.
 * <p>
 * A server without one advertises no {@code completions} capability, which is why {@link #NONE} answers
 * everything with {@link McpCompletion#EMPTY} rather than throwing — an endpoint that declares the
 * capability and then refuses every request would be worse than one that never declared it.
 */
@FunctionalInterface
public interface McpCompletionProvider {

    /** A provider that knows nothing, for an endpoint that offers no completions. */
    McpCompletionProvider NONE = (ref, argumentName, value) -> McpCompletion.EMPTY;

    /**
     * @param ref          what the argument belongs to
     * @param argumentName the argument being completed
     * @param value        what the user has typed so far, possibly empty
     */
    McpCompletion complete(McpCompletionRef ref, String argumentName, String value);
}
