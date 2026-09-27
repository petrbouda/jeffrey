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
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prompts are the plugin's own skill files, read through the same {@link McpSkillCatalogue} the
 * skills extension serves. Two things are worth holding: that a prompt is exactly the catalogue's skill
 * — name, description and the body after the frontmatter — with the titles and arguments this registry
 * adds, and that the real files still load; a skill the catalogue refuses is covered by
 * {@link McpSkillCatalogueTest}.
 */
class McpPromptRegistryTest {

    /** The one prompt a catalogue holding this single {@code SKILL.md} under {@code directory} serves. */
    private static McpPrompt promptFrom(String directory, String skillMd) {
        McpPromptRegistry registry = new McpPromptRegistry(new McpSkillCatalogue(
                Map.of(directory + "/SKILL.md", skillMd.getBytes(StandardCharsets.UTF_8))));
        assertEquals(1, registry.prompts().size(), "the catalogue must accept " + directory);
        return registry.prompts().getFirst();
    }

    /** A prompt for a skill named {@code name}, with the minimal frontmatter and this body. */
    private static McpPrompt prompt(String name, String body) {
        return promptFrom(name, "---\nname: " + name + "\ndescription: d\n---\n" + body);
    }

    @Nested
    class Parsing {

        @Test
        void readsTheNameDescriptionAndBody() {
            McpPrompt prompt = promptFrom("analyze-jfr", """
                    ---
                    name: analyze-jfr
                    description: What this skill is for.
                    ---

                    # Body

                    The instructions.
                    """);

            assertEquals("analyze-jfr", prompt.name());
            assertEquals("What this skill is for.", prompt.description());
            assertEquals("# Body\n\nThe instructions.\n", prompt.text());
        }

        @Test
        void ignoresFrontmatterKeysItDoesNotKnow() {
            McpPrompt prompt = promptFrom("jfr-sql", """
                    ---
                    name: jfr-sql
                    allowed-tools: mcp__jeffrey__*
                    description: Runs SQL.
                    ---
                    Body.
                    """);

            assertEquals("jfr-sql", prompt.name());
            assertEquals("Runs SQL.", prompt.description());
            assertEquals("Body.\n", prompt.text());
        }

        @Test
        void unquotesAQuotedValue() {
            McpPrompt prompt = promptFrom("heap-sql", """
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
        void titlesAKnownSkillFromTheMap() {
            assertEquals("Analyze a JFR Profile", prompt("analyze-jfr", "b").title());
        }

        /** A skill the small map does not know yet still gets a title, derived from its name. */
        @Test
        void derivesATitleForASkillTheMapDoesNotKnow() {
            assertEquals("Some New Skill", prompt("some-new-skill", "b").title());
        }

        @Test
        void declaresProfileIdForASingleProfileSkill() {
            McpPrompt prompt = prompt("analyze-jfr", "b");

            assertEquals(1, prompt.arguments().size());
            assertEquals("profileId", prompt.arguments().get(0).name());
            assertFalse(prompt.arguments().get(0).required());
        }

        @Test
        void declaresProfileIdAndBaselineForCompareJfr() {
            McpPrompt prompt = prompt("compare-jfr", "b");

            assertEquals(List.of("profileId", "baselineProfileId"),
                    prompt.arguments().stream().map(McpPrompt.Argument::name).toList());
        }

        @Test
        void declaresNoArgumentsForASkillThatTakesNone() {
            for (String name : List.of("analyze-hub", "profile-run", "regression-check", "report")) {
                assertTrue(prompt(name, "b").arguments().isEmpty(), name);
            }
        }

        /** A skill this table has never heard of gets no arguments, rather than a guess. */
        @Test
        void declaresNoArgumentsForASkillNotInTheTable() {
            assertTrue(prompt("some-new-skill", "b").arguments().isEmpty());
        }

        /**
         * A file the catalogue refuses is not a prompt either: returning half a prompt would put a
         * nameless entry in every client's menu.
         */
        @Test
        void servesNoPromptForASkillTheCatalogueRefused() {
            McpPromptRegistry registry = new McpPromptRegistry(new McpSkillCatalogue(Map.of(
                    "bad/SKILL.md", "# Just a document\n".getBytes(StandardCharsets.UTF_8))));

            assertTrue(registry.prompts().isEmpty());
        }
    }

    /**
     * {@code $ARGUMENTS} and the per-argument tokens are how a skill body reads what a client supplied
     * through {@code prompts/get} — the same mechanism Claude Code's own slash commands use, extended to
     * every other MCP client.
     */
    @Nested
    class Substitution {

        @Test
        void substitutesProfileIdAndArgumentsInTheBody() {
            McpPrompt prompt = prompt("analyze-jfr", "Scope: $ARGUMENTS. Profile: $profileId.\n");

            String rendered = prompt.render(Json.readTree("{\"profileId\":\"p-1\"}"));

            assertTrue(rendered.contains("Scope: p-1."), rendered);
            assertTrue(rendered.contains("Profile: p-1."), rendered);
        }

        @Test
        void substitutesBothProfilesForCompareJfr() {
            McpPrompt prompt = prompt("compare-jfr", "Compare $profileId against $baselineProfileId: $ARGUMENTS\n");

            String rendered = prompt.render(
                    Json.readTree("{\"profileId\":\"p-1\",\"baselineProfileId\":\"p-2\"}"));

            assertTrue(rendered.contains("Compare p-1 against p-2: p-1 p-2"), rendered);
        }

        @Test
        void leavesArgumentsAsAnEmptyStringWhenNoneAreSupplied() {
            McpPrompt prompt = prompt("profile-run", "Scope: [$ARGUMENTS]\n");

            assertTrue(prompt.render(null).contains("Scope: []"), prompt.render(null));
        }

        /**
         * A value a placeholder already read out of the body is not repeated in the JSON block — the
         * two would otherwise say the same thing twice, once inline and once underneath.
         */
        @Test
        void omitsTheContextBlockWhenTheBodyConsumesEveryArgument() {
            McpPrompt prompt = prompt("compare-jfr", "Compare $profileId against $baselineProfileId: $ARGUMENTS");

            String rendered = prompt.render(
                    Json.readTree("{\"profileId\":\"p-1\",\"baselineProfileId\":\"p-2\"}"));

            assertEquals("Compare p-1 against p-2: p-1 p-2", rendered);
        }

        /**
         * A body that never wrote a placeholder still gets what was supplied, appended underneath
         * rather than silently dropped.
         */
        @Test
        void appendsTheContextBlockOnlyForTheArgumentsTheBodyDidNotConsume() {
            McpPrompt prompt = prompt("analyze-jfr", "Read the profile.");

            String rendered = prompt.render(Json.readTree("{\"profileId\":\"p-1\"}"));

            ObjectNode expectedContext = Json.createObject().put("profileId", "p-1");
            assertEquals("Read the profile.\n\nCaller-provided workflow context (JSON):\n" + expectedContext,
                    rendered);
        }

        /** {@code $profileId2} is a different token; substituting {@code profileId} must leave it alone. */
        @Test
        void substitutesOnlyTheWholeTokenNotAPrefixMatch() {
            McpPrompt prompt = prompt("analyze-jfr", "Id: $profileId Other: $profileId2");

            String rendered = prompt.render(Json.readTree("{\"profileId\":\"p-1\"}"));

            assertEquals("Id: p-1 Other: $profileId2", rendered);
        }

        @Test
        void substitutesAnEmptyStringArgument() {
            McpPrompt prompt = prompt("analyze-jfr", "Scope: [$profileId]");

            String rendered = prompt.render(Json.readTree("{\"profileId\":\"\"}"));

            assertEquals("Scope: []", rendered);
        }

        /** The message is the client's only route back to a working call, so it lists what does exist. */
        @Test
        void refusesAnUndeclaredArgumentNamingTheDeclaredOnes() {
            McpPrompt prompt = prompt("analyze-jfr", "b\n");

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> prompt.render(Json.readTree("{\"unknown\":\"x\"}")));

            assertTrue(thrown.getMessage().contains("unknown"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("profileId"), thrown.getMessage());
        }
    }

    /**
     * Against the skills the build actually copied in, so a change to one of them that this reader
     * cannot follow fails here rather than in somebody's client.
     */
    @Nested
    class TheRealSkills {

        private final McpPromptRegistry registry = new McpPromptRegistry(McpSkillCatalogue.fromClasspath());

        /** One prompt per skill the catalogue serves, under the same name. */
        @Test
        void servesOnePromptPerCatalogueSkill() {
            List<String> skills = McpSkillCatalogue.fromClasspath().skills().stream().map(McpSkill::name).toList();

            assertEquals(10, registry.prompts().size());
            assertEquals(skills, registry.prompts().stream().map(McpPrompt::name).toList());
        }

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
         * {@code Map.copyOf} does not preserve insertion order, so an unsorted registry would answer
         * {@code prompts/list} in whatever order the classpath scan happened to visit the skill
         * directories in — a client should not see the menu reshuffle between two runs of the same
         * Jeffrey.
         */
        @Test
        void listsPromptsSortedByName() {
            List<String> names = registry.prompts().stream().map(McpPrompt::name).toList();

            List<String> sorted = names.stream().sorted(Comparator.naturalOrder()).toList();
            assertEquals(sorted, names);
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
