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

import java.util.Objects;

/**
 * What a {@code completion/complete} request is completing an argument <em>of</em>: one of this
 * server's resource templates, or one of its prompts.
 * <p>
 * The protocol spells the two as {@code ref/resource}, carrying a {@code uri}, and {@code ref/prompt},
 * carrying a {@code name}. They are one type here because a completion provider answers both the same
 * way — by the argument's name — and keeping them apart would mean two nearly identical methods on
 * every provider.
 *
 * @param type the reference type as the protocol spells it
 * @param name the template URI for a resource reference, or the prompt name for a prompt reference
 */
public record McpCompletionRef(String type, String name) {

    /** A reference to one of this server's resource templates. */
    public static final String TYPE_RESOURCE = "ref/resource";

    /** A reference to one of this server's prompts. */
    public static final String TYPE_PROMPT = "ref/prompt";

    public McpCompletionRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
    }

    public boolean isResource() {
        return TYPE_RESOURCE.equals(type);
    }

    public boolean isPrompt() {
        return TYPE_PROMPT.equals(type);
    }
}
