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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prompts are the plugin's own skill files, copied onto the classpath by the build. Two things are
 * worth holding: that the hand-rolled frontmatter reader does not quietly invent a prompt out of
 * something that is not one, and that the real files still parse — a skill whose frontmatter changes
 * shape would otherwise vanish from the server with nothing said.
 */
class McpPromptRegistryTest {

    @Nested
    class Parsing {

        @Test
        void readsTheNameDescriptionAndBody() {
            McpPrompt prompt = McpPromptRegistry.parse("""
                    ---
                    name: analyze-jfr
                    description: What this skill is for.
                    ---

                    # Body

                    The instructions.
                    """);

            assertEquals("analyze-jfr", prompt.name());
            assertEquals("What this skill is for.", prompt.description());
            assertTrue(prompt.text().startsWith("# Body"), prompt.text());
        }

        @Test
        void ignoresFrontmatterKeysItDoesNotKnow() {
            McpPrompt prompt = McpPromptRegistry.parse("""
                    ---
                    name: jfr-sql
                    allowed-tools: mcp__jeffrey__*
                    description: Runs SQL.
                    ---
                    Body.
                    """);

            assertEquals("jfr-sql", prompt.name());
            assertEquals("Runs SQL.", prompt.description());
        }

        @Test
        void unquotesAQuotedValue() {
            McpPrompt prompt = McpPromptRegistry.parse("""
                    ---
                    name: "heap-sql"
                    description: "Runs SQL against a heap dump."
                    ---
                    Body.
                    """);

            assertEquals("heap-sql", prompt.name());
            assertEquals("Runs SQL against a heap dump.", prompt.description());
        }

        @Test
        void titlesTheNameForAMenu() {
            assertEquals("Analyze Jfr", McpPromptRegistry.parse("""
                    ---
                    name: analyze-jfr
                    description: d
                    ---
                    b
                    """).title());
        }

        /**
         * A file that is not a skill is skipped rather than guessed at. Returning half a prompt would
         * put a nameless entry in every client's menu.
         */
        @Nested
        class Refusals {

            @Test
            void refusesAFileWithNoFrontmatter() {
                assertNull(McpPromptRegistry.parse("# Just a document\n\nwith no frontmatter.\n"));
            }

            @Test
            void refusesAnUnterminatedFrontmatter() {
                assertNull(McpPromptRegistry.parse("---\nname: x\ndescription: y\n"));
            }

            @Test
            void refusesFrontmatterWithoutAName() {
                assertNull(McpPromptRegistry.parse("---\ndescription: y\n---\nbody\n"));
            }

            @Test
            void refusesFrontmatterWithoutADescription() {
                assertNull(McpPromptRegistry.parse("---\nname: x\n---\nbody\n"));
            }

            @Test
            void refusesAnEmptyValue() {
                assertNull(McpPromptRegistry.parse("---\nname:\ndescription: y\n---\nbody\n"));
            }
        }
    }

    /**
     * Against the skills the build actually copied in, so a change to one of them that this reader
     * cannot follow fails here rather than in somebody's client.
     */
    @Nested
    class TheRealSkills {

        private final McpPromptRegistry registry = new McpPromptRegistry();

        @Test
        void loadsEveryPluginSkill() {
            List<McpPrompt> prompts = registry.prompts();

            assertFalse(prompts.isEmpty(), "the build copies the plugin's skills onto the classpath");
            assertTrue(prompts.stream().anyMatch(prompt -> prompt.name().equals("analyze-jfr")),
                    prompts.stream().map(McpPrompt::name).toList().toString());
        }

        @Test
        void everyPromptCarriesEnoughForAClientToShowIt() {
            for (McpPrompt prompt : registry.prompts()) {
                assertFalse(prompt.name().isBlank(), "a prompt needs a name");
                assertFalse(prompt.title().isBlank(), "a prompt needs a title for a menu");
                assertFalse(prompt.description().isBlank(), "a prompt needs a description");
                assertFalse(prompt.text().isBlank(), "a prompt with no body is not a workflow");
            }
        }

        @Test
        void looksOnePromptUpByName() {
            assertEquals("analyze-jfr", registry.prompt("analyze-jfr").name());
        }

        /**
         * The message is the client's only route back to a working call, so it lists what does exist.
         */
        @Test
        void refusesAnUnknownPromptNamingTheOnesItHas() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> registry.prompt("no-such-skill"));

            assertTrue(thrown.getMessage().contains("no-such-skill"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("analyze-jfr"), thrown.getMessage());
        }
    }
}
