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
package cafe.jeffrey.microscope.mcp.protocol;

import java.util.function.Supplier;

/**
 * What one endpoint offers: its tools, its prompts, its resources, the orientation it hands a client
 * at {@code server/discover}, the argument completions it can answer, the resource links its tools
 * carry, and the skills and tasks it serves over their extensions.
 * <p>
 * Every one of them is resolved lazily, per request, and for the same reason: {@code server/discover}
 * must answer even when building the toolset would fail, so they travel together rather than as
 * parameters that have to be kept in the same order at every call site.
 * <p>
 * An endpoint that completes nothing passes {@link McpCompletionProvider#NONE}, and the envelope then
 * advertises no {@code completions} capability at all rather than one that refuses everything. The
 * six-argument constructor serves no skills and follows no tasks; {@link #withSkills} and
 * {@link #withTasks} add them.
 *
 * @param tools         the toolset, resolved per request
 * @param prompts       the prompts, resolved per request
 * @param resources     the resources, resolved per request
 * @param instructions  how to use this server, returned with {@code server/discover}; null or blank for none
 * @param completions   the completion provider, resolved per request
 * @param resourceLinks the tool-to-resource mapping, resolved per request
 * @param skills        the skills extension; {@link McpSkillProvider#NONE} declares none
 * @param tasks         the tasks extension; {@link McpTaskProvider#NONE} declares none
 */
public record McpServerFeatures(
        Supplier<McpToolProvider> tools,
        Supplier<McpPromptProvider> prompts,
        Supplier<McpResourceProvider> resources,
        Supplier<String> instructions,
        Supplier<McpCompletionProvider> completions,
        Supplier<McpResourceLinker> resourceLinks,
        Supplier<McpSkillProvider> skills,
        Supplier<McpTaskProvider> tasks) {

    public McpServerFeatures {
        requirePresent(tools, "tools");
        requirePresent(prompts, "prompts");
        requirePresent(resources, "resources");
        requirePresent(instructions, "instructions");
        requirePresent(completions, "completions");
        requirePresent(resourceLinks, "resourceLinks");
        requirePresent(skills, "skills");
        requirePresent(tasks, "tasks");
    }

    public McpServerFeatures(
            Supplier<McpToolProvider> tools,
            Supplier<McpPromptProvider> prompts,
            Supplier<McpResourceProvider> resources,
            Supplier<String> instructions,
            Supplier<McpCompletionProvider> completions,
            Supplier<McpResourceLinker> resourceLinks) {

        this(tools, prompts, resources, instructions, completions, resourceLinks,
                () -> McpSkillProvider.NONE, () -> McpTaskProvider.NONE);
    }

    /** The same endpoint, serving these skills. */
    public McpServerFeatures withSkills(Supplier<McpSkillProvider> replacement) {
        return new McpServerFeatures(tools, prompts, resources, instructions, completions, resourceLinks,
                replacement, tasks);
    }

    /** The same endpoint, following these tasks. */
    public McpServerFeatures withTasks(Supplier<McpTaskProvider> replacement) {
        return new McpServerFeatures(tools, prompts, resources, instructions, completions, resourceLinks,
                skills, replacement);
    }

    private static void requirePresent(Supplier<?> supplier, String name) {
        if (supplier == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
