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

import cafe.jeffrey.microscope.mcp.protocol.McpMetaKeys;

/**
 * The keys Jeffrey adds to a tool's own {@code _meta} in {@code tools/list}, beside the protocol's
 * ({@link McpMetaKeys}): the host's size key and Jeffrey's two hints.
 */
public final class JeffreyMetaKeys {

    /**
     * The key through which Claude Code lets a tool raise the size of result it keeps inline instead of
     * spilling to a file. Other hosts ignore a key they do not know.
     */
    public static final String MAX_RESULT_SIZE_CHARS = "anthropic/maxResultSizeChars";

    /** A tool's {@link McpToolCost}, by name. */
    public static final String COST = "jeffrey/cost";

    /** A tool's {@link McpToolRequirement}s, by name, sorted; absent when it has none. */
    public static final String REQUIRES = "jeffrey/requires";

    private JeffreyMetaKeys() {
    }
}
