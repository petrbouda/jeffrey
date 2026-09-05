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
package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpPrompt;
import cafe.jeffrey.profile.mcp.McpPromptProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the {@code microscope} plugin's skills as MCP prompts.
 * <p>
 * The skills are what make a hundred tools usable: which family answers which question, the order to
 * run the heap tools in, that a comparison starts by asking whether two profiles are comparable at
 * all. A Claude Code or Codex user gets them from the plugin. Every other MCP client — Cursor, VS
 * Code, Kiro, anything hand-registered — cannot install a plugin and was left with the tools and no
 * account of how to use them.
 * <p>
 * The files are the plugin's own, copied onto the classpath by the build rather than duplicated in
 * source, so a skill edited for the plugin is the prompt this serves. Read once at startup: they
 * change when the jar does.
 */
public class McpPromptRegistry implements McpPromptProvider {

    private static final Logger LOG = LoggerFactory.getLogger(McpPromptRegistry.class);

    private static final String PROMPTS_PATTERN = "classpath*:mcp-prompts/*/SKILL.md";
    private static final String FRONTMATTER_DELIMITER = "---";
    private static final String NAME_KEY = "name:";
    private static final String DESCRIPTION_KEY = "description:";

    /**
     * The one argument every skill can use. The skills read as guidance without it, which is why it is
     * optional: a client showing a prompt in a menu should not demand an id before it can show anything.
     */
    private static final McpPrompt.Argument PROFILE_ID_ARGUMENT = new McpPrompt.Argument(
            "profileId", "The profile to work on, as listed by profiles_list. Optional.", false);

    private final Map<String, McpPrompt> promptsByName;

    public McpPromptRegistry() {
        this.promptsByName = load();
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

    private static Map<String, McpPrompt> load() {
        Map<String, McpPrompt> loaded = new LinkedHashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(PROMPTS_PATTERN);
            for (Resource resource : resources) {
                McpPrompt prompt = parse(read(resource));
                if (prompt != null) {
                    loaded.put(prompt.name(), prompt);
                }
            }
        } catch (IOException e) {
            // A Jeffrey without prompts is a Jeffrey that still serves every tool, so this is reported
            // rather than fatal: the skills are guidance, not the surface itself.
            LOG.warn("Could not read the MCP prompts, so none will be advertised: message={}",
                    e.getMessage());
            return Map.of();
        }
        LOG.debug("Loaded MCP prompts: count={}", loaded.size());
        return Map.copyOf(loaded);
    }

    private static String read(Resource resource) {
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Splits a skill into its frontmatter and its body.
     * <p>
     * Deliberately not a YAML parser. The frontmatter Jeffrey's skills carry is two scalar keys and
     * sometimes a third, and a parser would be a dependency and a schema for a format that already has
     * one — the plugin's. A file that does not look like a skill is skipped rather than guessed at.
     * <p>
     * Package-private rather than private so its edge cases can be exercised directly: the input is a
     * file written elsewhere in the repository, and the failure mode of every hand-rolled parser is to
     * return something plausible for input it did not anticipate.
     *
     * @return the prompt, or {@code null} when the content does not look like a skill at all
     */
    static McpPrompt parse(String content) {
        String normalized = content.stripLeading();
        if (!normalized.startsWith(FRONTMATTER_DELIMITER)) {
            return null;
        }
        int end = normalized.indexOf('\n' + FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (end < 0) {
            return null;
        }
        String frontmatter = normalized.substring(FRONTMATTER_DELIMITER.length(), end);
        String body = normalized.substring(end + FRONTMATTER_DELIMITER.length() + 1).stripLeading();

        String name = value(frontmatter, NAME_KEY);
        String description = value(frontmatter, DESCRIPTION_KEY);
        if (name == null || description == null) {
            return null;
        }
        return new McpPrompt(name, title(name), description, List.of(PROFILE_ID_ARGUMENT), body);
    }

    private static String value(String frontmatter, String key) {
        for (String line : frontmatter.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.startsWith(key)) {
                String value = trimmed.substring(key.length()).strip();
                return value.isEmpty() ? null : unquote(value);
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
