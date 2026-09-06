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

package cafe.jeffrey.profile.ai.chat;

/**
 * Provider-agnostic binding that gives a model access to a profile's analysis tools. The two
 * supported backends consume different representations of the same capability:
 * <ul>
 *     <li>The Spring AI backend invokes in-process {@code @Tool}-annotated objects.</li>
 *     <li>The Claude Code backend connects to an in-JVM MCP server over HTTP.</li>
 * </ul>
 * A service supplies both representations; each backend uses the one it understands.
 *
 * @param springAiTools the {@code @Tool}-annotated object passed to the Spring AI tool-calling path
 * @param mcpToolset    the MCP exposure for the Claude Code path (may be null when unavailable)
 * @param sourceAccess  the checkout the model may read while answering, or null for none. Only the
 *                      Claude Code path can use it — it runs a real agent with file tools, where the
 *                      Spring AI path has only the {@code @Tool} objects it was handed
 */
public record ToolBinding(
        Object springAiTools,
        McpToolset mcpToolset,
        SourceAccess sourceAccess
) {

    /**
     * Tools without source access, which is every binding until an installation turns it on.
     */
    public static ToolBinding of(Object springAiTools, McpToolset mcpToolset) {
        return new ToolBinding(springAiTools, mcpToolset, null);
    }
}
