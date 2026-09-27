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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpPromptProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serves the {@code microscope} plugin's skills as MCP prompts.
 * <p>
 * The skills are what make a hundred tools usable: which family answers which question, the order to
 * run the heap tools in, that a comparison starts by asking whether two profiles are comparable at
 * all. A Claude Code, Codex or Gemini CLI user gets them from the plugin. Every other MCP client — Cursor, VS
 * Code, Kiro, anything hand-registered — cannot install a plugin and was left with the tools and no
 * account of how to use them.
 * <p>
 * The prompts come from the same {@link McpSkillCatalogue} the skills extension serves — one parse of
 * the plugin's own files, copied onto the classpath by the build — so a skill edited for the plugin is
 * the prompt this serves, and a skill the catalogue refuses is no prompt either. What a prompt adds to
 * the skill is its menu title and the arguments a client may fill in.
 */
public class McpPromptRegistry implements McpPromptProvider {

    /**
     * The profile a single-profile skill reads. Optional, like every prompt argument here: a client
     * showing a prompt in a menu should not demand an id before it can show anything, and the skills
     * read as guidance even with nothing filled in.
     */
    private static final McpPrompt.Argument PROFILE_ID_ARGUMENT = new McpPrompt.Argument(
            "profileId", "The profile to work on, as listed by profiles_list. Optional.", false);

    /** The second profile a comparison reads its baseline from. */
    private static final McpPrompt.Argument BASELINE_PROFILE_ID_ARGUMENT = new McpPrompt.Argument(
            "baselineProfileId", "The profile to compare against, as listed by profiles_list. Optional.", false);

    private static final List<McpPrompt.Argument> PROFILE_ID_ONLY = List.of(PROFILE_ID_ARGUMENT);
    private static final List<McpPrompt.Argument> PROFILE_AND_BASELINE =
            List.of(PROFILE_ID_ARGUMENT, BASELINE_PROFILE_ID_ARGUMENT);
    private static final List<McpPrompt.Argument> NO_ARGUMENTS = List.of();

    /**
     * What each skill takes, keyed by its {@code name}. A skill that reads one profile takes
     * {@code profileId}; a comparison takes it and a {@code baselineProfileId}; a skill that produces a
     * profile, reaches a hub or is pure guidance takes neither. A skill missing from this table — the
     * plugin grows one nobody has told the registry about yet — gets no arguments, which is the safer
     * default: a client cannot offer an id the prompt does not know what to do with.
     */
    private static final Map<String, List<McpPrompt.Argument>> ARGUMENTS_BY_SKILL = Map.ofEntries(
            Map.entry("analyze-jfr", PROFILE_ID_ONLY),
            Map.entry("analyze-heap", PROFILE_ID_ONLY),
            Map.entry("advise-jfr", PROFILE_ID_ONLY),
            Map.entry("jfr-sql", PROFILE_ID_ONLY),
            Map.entry("heap-sql", PROFILE_ID_ONLY),
            Map.entry("compare-jfr", PROFILE_AND_BASELINE),
            Map.entry("analyze-hub", NO_ARGUMENTS),
            Map.entry("profile-run", NO_ARGUMENTS),
            Map.entry("regression-check", NO_ARGUMENTS),
            Map.entry("report", NO_ARGUMENTS));

    /**
     * The menu title for each skill. A small, explicit map rather than {@link #title(String)}'s
     * word-split for the ten skills this server actually ships, because splitting on {@code -} alone
     * reads {@code jfr} and {@code sql} as ordinary words ("Analyze Jfr", "Jfr Sql") instead of the
     * acronyms they are.
     */
    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("analyze-jfr", "Analyze a JFR Profile"),
            Map.entry("analyze-heap", "Analyze a Heap Dump"),
            Map.entry("analyze-hub", "Analyze a Hub Session"),
            Map.entry("advise-jfr", "Advise on a Profile"),
            Map.entry("compare-jfr", "Compare Two Profiles"),
            Map.entry("jfr-sql", "Query a Profile with SQL"),
            Map.entry("heap-sql", "Query a Heap Dump with SQL"),
            Map.entry("profile-run", "Profile a Run"),
            Map.entry("regression-check", "Check for a Regression"),
            Map.entry("report", "Report a Finding"));

    private final Map<String, McpPrompt> promptsByName;

    /**
     * Sorted by name, so {@code prompts/list} answers in the same order on every run; the catalogue's
     * URI order differs from it when one name is a prefix of another.
     */
    public McpPromptRegistry(McpSkillCatalogue catalogue) {
        Map<String, McpPrompt> prompts = new TreeMap<>();
        for (McpSkillCatalogue.SkillText text : catalogue.texts()) {
            McpPrompt prompt = prompt(text);
            prompts.put(prompt.name(), prompt);
        }
        this.promptsByName = Collections.unmodifiableMap(prompts);
    }

    @Override
    public List<McpPrompt> prompts() {
        return List.copyOf(promptsByName.values());
    }

    @Override
    public McpPrompt prompt(String name) {
        McpPrompt prompt = promptsByName.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException(
                    "No prompt named '" + name + "'. Available: " + String.join(", ", promptsByName.keySet()));
        }
        return prompt;
    }

    /** One skill as a prompt: its name, description and body, with the title and arguments added here. */
    private static McpPrompt prompt(McpSkillCatalogue.SkillText text) {
        McpSkill skill = text.skill();
        String name = skill.name();
        String title = TITLES.getOrDefault(name, title(name));
        List<McpPrompt.Argument> arguments = ARGUMENTS_BY_SKILL.getOrDefault(name, NO_ARGUMENTS);
        return new McpPrompt(name, title, skill.description(), arguments, text.body());
    }

    /**
     * {@code analyze-jfr} shown as "Analyze Jfr" — a menu label, from the only name the file carries.
     */
    private static String title(String name) {
        List<String> words = new ArrayList<>();
        for (String word : name.split("-")) {
            if (!word.isEmpty()) {
                words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", words);
    }
}
