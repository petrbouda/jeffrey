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

package cafe.jeffrey.microscope.mcp.protocol.testing;

import cafe.jeffrey.microscope.mcp.protocol.McpCompletionProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceLinker;
import cafe.jeffrey.microscope.mcp.protocol.McpResourceProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpServerFeatures;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;

import java.util.function.Supplier;

/**
 * The endpoint most protocol tests stand up: tools, prompts and resources, and nothing else — no
 * instructions, no completions, no resource links, no skills and no tasks. Shipped in the test-jar, so
 * the adapter's envelope tests stand up the same endpoint.
 */
public final class McpTestFeatures {

    private McpTestFeatures() {
    }

    /**
     * Declares no {@code completions} capability, since {@link McpCompletionProvider#NONE} completes
     * nothing; {@link McpServerFeatures#withSkills} and {@link McpServerFeatures#withTasks} add the rest.
     */
    public static McpServerFeatures of(
            Supplier<McpToolProvider> tools,
            Supplier<McpPromptProvider> prompts,
            Supplier<McpResourceProvider> resources) {
        return new McpServerFeatures(
                tools, prompts, resources, () -> null, () -> McpCompletionProvider.NONE, () -> McpResourceLinker.NONE);
    }
}
