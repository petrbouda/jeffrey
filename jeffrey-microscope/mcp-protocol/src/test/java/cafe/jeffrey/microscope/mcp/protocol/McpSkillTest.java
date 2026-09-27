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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skills extension's own types: a file with the digest and size a host verifies it by, a skill
 * with its frontmatter and complete manifest, and the provider an endpoint serves them from.
 */
class McpSkillTest {

    private static final String SKILL_URI = "skill://analyze/SKILL.md";
    private static final String GUIDE_URI = "skill://analyze/references/guide.md";
    private static final byte[] SKILL_BYTES = "---\nname: analyze\ndescription: d\n---\nBody.\n"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] GUIDE_BYTES = "# Guide — ünïcode\n".getBytes(StandardCharsets.UTF_8);

    static McpSkillFile file(String uri, byte[] bytes) {
        return new McpSkillFile(uri, McpResource.TEXT_MARKDOWN, bytes);
    }

    static ObjectNode frontmatter(String name, String description) {
        return McpJson.createObject().put("name", name).put("description", description);
    }

    static McpSkill analyze() {
        return new McpSkill(SKILL_URI, frontmatter("analyze", "d"),
                List.of(file(SKILL_URI, SKILL_BYTES), file(GUIDE_URI, GUIDE_BYTES)));
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Nested
    class Files {

        @Test
        void digestIsSha256OfTheRawBytesInLowercaseHex() {
            McpSkillFile guide = file(GUIDE_URI, GUIDE_BYTES);

            assertEquals(sha256(GUIDE_BYTES), guide.digest());
            assertTrue(guide.digest().matches("sha256:[0-9a-f]{64}"), guide.digest());
        }

        /** The byte count, not the character count: the guide carries multi-byte characters. */
        @Test
        void sizeIsTheByteLength() {
            McpSkillFile guide = file(GUIDE_URI, GUIDE_BYTES);

            assertEquals(GUIDE_BYTES.length, guide.size());
            assertNotEquals("# Guide — ünïcode\n".length(), guide.size());
        }

        @Test
        void decodesItsTextAsUtf8() {
            assertEquals("# Guide — ünïcode\n", file(GUIDE_URI, GUIDE_BYTES).text());
        }

        /** The digest was published for these bytes; nobody holding the array may change them. */
        @Test
        void copiesTheBytesInAndOut() {
            byte[] source = GUIDE_BYTES.clone();
            McpSkillFile guide = file(GUIDE_URI, source);
            source[0] = 'X';
            guide.bytes()[1] = 'Y';

            assertArrayEquals(GUIDE_BYTES, guide.bytes());
            assertEquals(sha256(GUIDE_BYTES), guide.digest());
        }

        @Test
        void comparesByContent() {
            assertEquals(file(GUIDE_URI, GUIDE_BYTES.clone()), file(GUIDE_URI, GUIDE_BYTES.clone()));
            assertEquals(file(GUIDE_URI, GUIDE_BYTES.clone()).hashCode(),
                    file(GUIDE_URI, GUIDE_BYTES.clone()).hashCode());
        }

        @Test
        void refusesAMissingField() {
            assertThrows(IllegalArgumentException.class, () -> new McpSkillFile(" ", "text/markdown", GUIDE_BYTES));
            assertThrows(IllegalArgumentException.class, () -> new McpSkillFile(GUIDE_URI, null, GUIDE_BYTES));
            assertThrows(IllegalArgumentException.class, () -> new McpSkillFile(GUIDE_URI, "text/markdown", null));
        }
    }

    @Nested
    class Skills {

        @Test
        void rendersTheEntryTheSpecificationDefines() {
            JsonNode entry = analyze().toJson();

            assertEquals(SKILL_URI, entry.path("uri").asString());
            assertEquals(frontmatter("analyze", "d"), entry.path("frontmatter"));
            JsonNode resources = entry.path("resources");
            assertEquals(2, resources.size());
            assertEquals(SKILL_URI, resources.get(0).path("uri").asString());
            assertEquals(sha256(SKILL_BYTES), resources.get(0).path("digest").asString());
            assertEquals(SKILL_BYTES.length, resources.get(0).path("size").asInt());
            assertEquals(GUIDE_URI, resources.get(1).path("uri").asString());
            assertEquals(3, resources.get(1).size(), "uri, digest and size only: " + resources.get(1));
        }

        @Test
        void namesItselfFromItsFrontmatter() {
            assertEquals("analyze", analyze().name());
            assertEquals("d", analyze().description());
        }

        /** Every key the author wrote passes through, not a curated subset. */
        @Test
        void keepsEveryFrontmatterKey() {
            ObjectNode written = frontmatter("analyze", "d").put("allowed-tools", "Read Grep");
            McpSkill skill = new McpSkill(SKILL_URI, written, List.of(file(SKILL_URI, SKILL_BYTES)));

            assertEquals("Read Grep", skill.toJson().path("frontmatter").path("allowed-tools").asString());
        }

        @Test
        void copiesItsFrontmatterInAndOut() {
            ObjectNode written = frontmatter("analyze", "d");
            McpSkill skill = new McpSkill(SKILL_URI, written, List.of(file(SKILL_URI, SKILL_BYTES)));
            written.put("name", "changed");
            skill.frontmatter().put("description", "changed");

            assertEquals(frontmatter("analyze", "d"), skill.frontmatter());
        }

        @Nested
        class Refusals {

            @Test
            void refusesAUriThatIsNotASkillMd() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill("skill://analyze/README.md",
                        frontmatter("analyze", "d"), List.of(file("skill://analyze/README.md", SKILL_BYTES))));
            }

            /** The last path segment is the skill's name, so a host can read it off the URI alone. */
            @Test
            void refusesANameThatIsNotTheLastPathSegment() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        frontmatter("other", "d"), List.of(file(SKILL_URI, SKILL_BYTES))));
            }

            @Test
            void refusesFrontmatterWithoutANameOrDescription() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        McpJson.createObject().put("name", "analyze"), List.of(file(SKILL_URI, SKILL_BYTES))));
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        McpJson.createObject().put("description", "d"), List.of(file(SKILL_URI, SKILL_BYTES))));
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        frontmatter("analyze", " "), List.of(file(SKILL_URI, SKILL_BYTES))));
            }

            /** The manifest is complete: it always lists SKILL.md itself. */
            @Test
            void refusesAManifestWithoutTheSkillMd() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        frontmatter("analyze", "d"), List.of(file(GUIDE_URI, GUIDE_BYTES))));
            }

            @Test
            void refusesAFileOutsideTheSkillsDirectory() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        frontmatter("analyze", "d"),
                        List.of(file(SKILL_URI, SKILL_BYTES), file("skill://report/SKILL.md", GUIDE_BYTES))));
            }

            @Test
            void refusesAFileListedTwice() {
                assertThrows(IllegalArgumentException.class, () -> new McpSkill(SKILL_URI,
                        frontmatter("analyze", "d"),
                        List.of(file(SKILL_URI, SKILL_BYTES), file(SKILL_URI, SKILL_BYTES))));
            }
        }
    }

    @Nested
    class Providers {

        private final McpSkillProvider provider = () -> List.of(analyze());

        @Test
        void findsASkillByTheUriOfItsSkillMd() {
            assertEquals(analyze(), provider.skill(SKILL_URI));
        }

        /** The specification's code for a skill it does not serve, the same as an unknown resource. */
        @Test
        void answersAnUnknownSkillWithInvalidParams() {
            McpProtocolException refusal = assertThrows(McpProtocolException.class,
                    () -> provider.skill("skill://missing/SKILL.md"));

            assertEquals(McpErrorCode.INVALID_PARAMS, refusal.code());
            assertTrue(refusal.getMessage().contains("skill://missing/SKILL.md"), refusal.getMessage());
        }

        /** A supporting file is not a skill, even though it is readable. */
        @Test
        void answersASupportingFilesUriAsNoSkill() {
            assertThrows(McpProtocolException.class, () -> provider.skill(GUIDE_URI));
        }

        @Test
        void findsAnyFileInAnyManifest() {
            Optional<McpSkillFile> guide = provider.file(GUIDE_URI);

            assertTrue(guide.isPresent());
            assertArrayEquals(GUIDE_BYTES, guide.get().bytes());
            assertFalse(provider.file("skill://analyze/references/missing.md").isPresent());
        }

        /** What decides whether the extension is declared: at least one skill. */
        @Test
        void servesAnyExactlyWhenItHasASkill() {
            assertTrue(provider.servesAny());
            McpSkillProvider empty = List::of;
            assertFalse(empty.servesAny());
            assertFalse(McpSkillProvider.NONE.servesAny());
        }

        @Test
        void noneServesNothing() {
            assertTrue(McpSkillProvider.NONE.skills().isEmpty());
            assertFalse(McpSkillProvider.NONE.file(SKILL_URI).isPresent());
            assertThrows(McpProtocolException.class, () -> McpSkillProvider.NONE.skill(SKILL_URI));
        }
    }
}
