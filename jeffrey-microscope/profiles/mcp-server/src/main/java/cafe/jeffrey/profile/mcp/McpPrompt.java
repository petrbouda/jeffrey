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

import java.util.List;

/**
 * A workflow the server offers by name, which a client can insert into a conversation.
 * <p>
 * Jeffrey's workflows — how to read a JFR profile, how to work a heap dump, how to compare two runs —
 * ship as skills in the {@code microscope} plugin, and a client that has the plugin needs nothing
 * here. Prompts exist for the clients that cannot take a plugin: Cursor, VS Code, Kiro and anything
 * else that speaks MCP and nothing else. Without them those clients get a hundred tools and no account
 * of which to reach for first, which is the part that actually makes the surface usable.
 *
 * @param name      the prompt name, as the client lists it
 * @param title     a human-readable name for a menu
 * @param description what the workflow is for
 * @param arguments what the caller may supply; all optional, since a workflow reads as guidance even
 *                  with nothing filled in
 * @param text      the body handed back as the prompt's message
 */
public record McpPrompt(
        String name,
        String title,
        String description,
        List<Argument> arguments,
        String text) {

    public McpPrompt {
        arguments = List.copyOf(arguments);
    }

    public record Argument(String name, String description, boolean required) {
    }
}
