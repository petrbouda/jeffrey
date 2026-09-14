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
