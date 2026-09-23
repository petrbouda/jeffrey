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
