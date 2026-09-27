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

import cafe.jeffrey.microscope.mcp.protocol.McpErrorCode;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolException;
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skills served over MCP are the plugin's own files, copied onto the classpath by the build. The
 * real ones are checked against the plugin directory byte for byte; the loading rules against small
 * in-memory skills, one broken way at a time.
 */
class McpSkillCatalogueTest {

    /** Where the plugin keeps its skills, from this module's directory (the test's working directory). */
    private static final Path PLUGIN_SKILLS = Path.of("../../jeffrey-claude-plugin/skills");

    private static final int PLUGIN_SKILL_COUNT = 10;

    private static final String GOOD = "---\nname: good\ndescription: A skill that loads.\n---\n\n# Good\n\nBody.\n";

    /**
     * What a host resolves against the skill's root: a backtick-quoted relative file path — a directory
     * and a file of a text type, as in {@code `references/guide.md`} — or the target of a Markdown link.
     * A command line or an absolute path in backticks is not a reference.
     */
    private static final Pattern RELATIVE_REFERENCE = Pattern.compile(
            "`((?:\\.{1,2}/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_./-]*\\.(?:md|txt|json|ya?ml|py|sh))`"
                    + "|\\]\\(([^)\\s#]+)\\)");

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every file under the plugin's skills directory, keyed as the catalogue keys it: {@code <skill>/<path>}. */
    private static Map<String, byte[]> pluginFiles() {
        try (Stream<Path> walk = Files.walk(PLUGIN_SKILLS)) {
            Map<String, byte[]> files = new TreeMap<>();
            walk.filter(Files::isRegularFile).forEach(file -> files.put(
                    PLUGIN_SKILLS.relativize(file).toString().replace('\\', '/'), read(file)));
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A catalogue over the good skill and whatever else the test adds. */
    private static McpSkillCatalogue withGood(Map<String, byte[]> others) {
        Map<String, byte[]> files = new LinkedHashMap<>(others);
        files.put("good/SKILL.md", utf8(GOOD));
        return new McpSkillCatalogue(files);
    }

    private static McpSkillCatalogue withGood(String path, String text) {
        return withGood(Map.of(path, utf8(text)));
    }

    /** The URI a reference in the file at {@code base} names, or the reference itself when it is no URI. */
    private static String resolve(String base, String reference) {
        try {
            return URI.create(base).resolve(reference).toString();
        } catch (IllegalArgumentException e) {
            return reference;
        }
    }

    private static List<String> names(McpSkillCatalogue catalogue) {
        return catalogue.skills().stream().map(McpSkill::name).toList();
    }

    private static ObjectNode frontmatterOf(String yaml) {
        McpSkillCatalogue catalogue = new McpSkillCatalogue(Map.of("probe/SKILL.md",
                utf8("---\nname: probe\ndescription: d\n" + yaml + "\n---\nBody.\n")));
        assertEquals(List.of("probe"), names(catalogue), "the probe skill must load");
        return catalogue.skills().getFirst().frontmatter();
    }

    @Nested
    class TheRealSkills {

        private final McpSkillCatalogue catalogue = McpSkillCatalogue.fromClasspath();

        @Test
        void servesTheTenPluginSkills() {
            Set<String> directories = pluginFiles().keySet().stream()
                    .map(path -> path.substring(0, path.indexOf('/')))
                    .collect(Collectors.toCollection(TreeSet::new));

            assertEquals(PLUGIN_SKILL_COUNT, catalogue.skills().size(), names(catalogue).toString());
            assertEquals(List.copyOf(directories), names(catalogue));
        }

        /** What a host verifies every read against is exactly the plugin's file on disk. */
        @Test
        void publishesTheDigestAndSizeOfEveryPluginFile() {
            Map<String, byte[]> expected = pluginFiles();
            Map<String, McpSkillFile> served = new TreeMap<>();
            for (McpSkill skill : catalogue.skills()) {
                for (McpSkillFile file : skill.files()) {
                    served.put(file.uri().substring(McpSkill.SCHEME.length()), file);
                }
            }

            assertEquals(expected.keySet(), served.keySet());
            expected.forEach((path, bytes) -> {
                assertEquals(sha256(bytes), served.get(path).digest(), path);
                assertEquals(bytes.length, served.get(path).size(), path);
            });
        }

        /**
         * A YAML 1.1 reader turns {@code yes}, {@code on} or {@code no} into booleans and a date into a
         * timestamp; the frontmatter a host compares field by field against the file would then differ
         * from what the author wrote. Every value the plugin writes is text, and must stay text.
         */
        @Test
        void everyFrontmatterValueRoundTripsAsAString() {
            for (McpSkill skill : catalogue.skills()) {
                skill.frontmatter().properties().forEach(field ->
                        assertTrue(field.getValue().isString(), skill.name() + "." + field.getKey() + " = " + field.getValue()));
            }
        }

        @Test
        void keepsEveryFrontmatterKeyTheAuthorWrote() {
            ObjectNode profileRun = catalogue.skill("skill://profile-run/SKILL.md").frontmatter();

            assertEquals(Set.of("name", "description", "allowed-tools", "argument-hint"),
                    profileRun.propertyNames().stream().collect(Collectors.toSet()));
            assertEquals("[what to run] [cpu|wall|alloc|lock]", profileRun.path("argument-hint").asString());
        }

        @Test
        void listsTheReportsReferenceInTheReportsManifest() {
            List<String> report = catalogue.skill("skill://report/SKILL.md").files().stream()
                    .map(McpSkillFile::uri)
                    .toList();

            assertEquals(List.of("skill://report/SKILL.md", "skill://report/references/tool-prefixes.md"), report);
        }

        @Test
        void sortsTheSkillsAndEachManifestByUri() {
            List<String> skills = catalogue.skills().stream().map(McpSkill::uri).toList();
            assertEquals(skills.stream().sorted().toList(), skills);
            for (McpSkill skill : catalogue.skills()) {
                List<String> files = skill.files().stream().map(McpSkillFile::uri).toList();
                assertEquals(files.stream().sorted(Comparator.naturalOrder()).toList(), files, skill.uri());
            }
        }

        @Test
        void servesEveryFileAsMarkdown() {
            for (McpSkill skill : catalogue.skills()) {
                for (McpSkillFile file : skill.files()) {
                    assertEquals("text/markdown", file.mimeType(), file.uri());
                }
            }
        }

        /**
         * A host resolves a relative reference against the skill's own root and may read only files the
         * manifest lists, so a reference that climbs out of the skill is unreadable over MCP. Each skill
         * therefore spells the tool-name prefixes out itself rather than pointing into {@code report}.
         */
        @Test
        void everyRelativeLinkResolvesInsideTheSameSkillsManifest() {
            List<String> dangling = new ArrayList<>();
            for (McpSkill skill : catalogue.skills()) {
                Set<String> manifest = skill.files().stream().map(McpSkillFile::uri).collect(Collectors.toSet());
                for (McpSkillFile file : skill.files()) {
                    Matcher matcher = RELATIVE_REFERENCE.matcher(file.text());
                    while (matcher.find()) {
                        String reference = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                        if (reference.contains("://")) {
                            continue;
                        }
                        if (!manifest.contains(resolve(file.uri(), reference))) {
                            dangling.add(file.uri() + " -> " + reference);
                        }
                    }
                }
            }

            assertTrue(dangling.isEmpty(), String.join("\n", dangling));
        }

        /** The same files feed the prompts: each with the text after its frontmatter. */
        @Test
        void handsEachSkillsBodyToThePrompts() {
            List<McpSkillCatalogue.SkillText> texts = catalogue.texts();

            assertEquals(PLUGIN_SKILL_COUNT, texts.size());
            for (McpSkillCatalogue.SkillText text : texts) {
                assertFalse(text.body().isBlank(), text.skill().uri());
                assertFalse(text.body().startsWith("---"), text.skill().uri());
                assertFalse(text.body().contains("allowed-tools:"), text.skill().uri());
            }
        }
    }

    @Nested
    class Loading {

        @Test
        void addressesEveryFileUnderItsSkill() {
            McpSkillCatalogue catalogue = withGood("good/references/guide.md", "# Guide\n");

            McpSkill good = catalogue.skill("skill://good/SKILL.md");
            assertEquals(List.of("skill://good/SKILL.md", "skill://good/references/guide.md"),
                    good.files().stream().map(McpSkillFile::uri).toList());
            assertEquals("# Guide\n", catalogue.file("skill://good/references/guide.md").orElseThrow().text());
        }

        @Test
        void keepsTheBodyAfterTheFrontmatter() {
            McpSkillCatalogue.SkillText text = withGood(Map.of()).texts().getFirst();

            assertEquals("# Good\n\nBody.\n", text.body());
        }

        /** A skill that fails its checks is left out; the good one beside it is still served. */
        @Test
        void skipsABadSkillAndKeepsTheRest() {
            McpSkillCatalogue catalogue = withGood("bad/SKILL.md", "# no frontmatter\n");

            assertEquals(List.of("good"), names(catalogue));
            assertTrue(catalogue.file("skill://bad/SKILL.md").isEmpty());
        }

        @Test
        void answersAnUnknownSkillWithInvalidParams() {
            McpProtocolException refusal = assertThrows(McpProtocolException.class,
                    () -> withGood(Map.of()).skill("skill://missing/SKILL.md"));

            assertEquals(McpErrorCode.INVALID_PARAMS, refusal.code());
        }

        @Test
        void servesNothingFromAnEmptyClasspath() {
            assertTrue(new McpSkillCatalogue(Map.of()).skills().isEmpty());
        }
    }

    /** The frontmatter is rendered verbatim: every key, and every value as the author wrote it. */
    @Nested
    class Frontmatter {

        @Test
        void unquotesAQuotedValue() {
            ObjectNode frontmatter = frontmatterOf("argument-hint: \"[profile-id] [cpu|wall]\"");

            assertEquals("[profile-id] [cpu|wall]", frontmatter.path("argument-hint").asString());
        }

        @Test
        void keepsYamlOneOneBooleanWordsAsText() {
            ObjectNode frontmatter = frontmatterOf("a: yes\nb: on\nc: no\nd: off\ne: Y");

            for (String key : List.of("a", "b", "c", "d", "e")) {
                assertTrue(frontmatter.path(key).isString(), key + " = " + frontmatter.path(key));
            }
            assertEquals("yes", frontmatter.path("a").asString());
            assertEquals("off", frontmatter.path("d").asString());
        }

        @Test
        void keepsDatesAndSexagesimalsAndOctalsAsText() {
            ObjectNode frontmatter = frontmatterOf("released: 2026-09-26\nduration: 1:30\nmode: 0755");

            assertEquals("2026-09-26", frontmatter.path("released").asString());
            assertEquals("1:30", frontmatter.path("duration").asString());
            assertEquals("0755", frontmatter.path("mode").asString());
        }

        /** What JSON has a type for keeps it, the way YAML 1.2's core schema reads it. */
        @Test
        void readsCoreSchemaScalarsAsTheirJsonTypes() {
            ObjectNode frontmatter = frontmatterOf("flag: true\ncount: 42\nratio: 0.5\nnothing: null");

            assertTrue(frontmatter.path("flag").isBoolean());
            assertEquals(42, frontmatter.path("count").asInt());
            assertEquals(0.5, frontmatter.path("ratio").asDouble());
            assertTrue(frontmatter.path("nothing").isNull());
        }

        /** A sentence holding {@code ": "} is valid YAML once it is quoted, and reads back unquoted. */
        @Test
        void readsAQuotedValueHoldingAColonAsText() {
            ObjectNode frontmatter = frontmatterOf(
                    "summary: \"the machine underneath: \\\"GC\\\", safepoints\"");

            assertEquals("the machine underneath: \"GC\", safepoints", frontmatter.path("summary").asString());
        }

        @Test
        void keepsNestedMetadataAsAnObject() {
            JsonNode metadata = frontmatterOf("metadata:\n  owner: jeffrey\n  tags: [jfr, heap]").path("metadata");

            assertEquals("jeffrey", metadata.path("owner").asString());
            assertEquals(2, metadata.path("tags").size());
        }
    }

    /** Each broken skill is skipped with a warning; the good one beside it always survives. */
    @Nested
    class Refusals {

        private void assertSkipped(Map<String, byte[]> bad) {
            assertEquals(List.of("good"), names(withGood(bad)));
        }

        private void assertSkipped(String path, String text) {
            assertSkipped(Map.of(path, utf8(text)));
        }

        @Test
        void refusesASkillWithNoSkillMd() {
            assertSkipped("bad/references/guide.md", "# Guide\n");
        }

        @Test
        void refusesAFileWithNoFrontmatter() {
            assertSkipped("bad/SKILL.md", "# Just a document\n\nwith no frontmatter.\n");
        }

        @Test
        void refusesAnUnterminatedFrontmatter() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\n");
        }

        @Test
        void refusesFrontmatterWithoutAName() {
            assertSkipped("bad/SKILL.md", "---\ndescription: y\n---\nbody\n");
        }

        @Test
        void refusesFrontmatterWithoutADescription() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\n---\nbody\n");
        }

        @Test
        void refusesAnEmptyName() {
            assertSkipped("bad/SKILL.md", "---\nname:\ndescription: y\n---\nbody\n");
        }

        @Test
        void refusesANameThatIsNotTheDirectory() {
            assertSkipped("bad/SKILL.md", "---\nname: other\ndescription: y\n---\nbody\n");
        }

        /** The Agent Skills naming rule: lowercase letters, digits and single hyphens. */
        @Test
        void refusesANameOutsideTheNamingRules() {
            assertSkipped("Bad_Skill/SKILL.md", "---\nname: Bad_Skill\ndescription: y\n---\nbody\n");
        }

        @Test
        void refusesFrontmatterThatIsNotAMapping() {
            assertSkipped("bad/SKILL.md", "---\n- name\n- description\n---\nbody\n");
        }

        @Test
        void refusesFrontmatterThatIsNotYaml() {
            assertSkipped("bad/SKILL.md", "---\nname: [bad\ndescription: y\n---\nbody\n");
        }

        /**
         * A plain value may not hold {@code ": "}: strict YAML stops there, and so does the catalogue — the
         * frontmatter a host verifies field by field has to be what any YAML parser reads. The author
         * quotes the value instead.
         */
        @Test
        void refusesAnUnquotedColonInsideAValue() {
            assertSkipped("bad/SKILL.md",
                    "---\nname: bad\ndescription: the machine underneath: garbage collection\n---\nbody\n");
        }

        @Test
        void refusesAnUnquotedColonBesideQuotesAndBackslashes() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: say \"hi\": then C:\\temp\n---\nbody\n");
        }

        /** No line is re-read on its own: one unquoted colon refuses the whole frontmatter. */
        @Test
        void refusesAnUnquotedColonEvenWhenEveryOtherLineIsValid() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\nflag: true\nsummary: a: b\n---\nbody\n");
        }

        @Test
        void refusesADuplicateKey() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\nname: bad\ndescription: y\n---\nbody\n");
        }

        @Test
        void refusesAByteOrderMark() {
            byte[] text = utf8("---\nname: bad\ndescription: y\n---\nbody\n");
            byte[] withBom = new byte[text.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(text, 0, withBom, 3, text.length);

            assertSkipped(Map.of("bad/SKILL.md", withBom));
        }

        /** Served as UTF-8 text, so bytes that do not decode could not match their digest after a read. */
        @Test
        void refusesASupportingFileThatIsNotUtf8() {
            assertSkipped(Map.of(
                    "bad/SKILL.md", utf8("---\nname: bad\ndescription: y\n---\nbody\n"),
                    "bad/data.md", new byte[]{(byte) 0xC3, (byte) 0x28}));
        }

        @Test
        void refusesMoreThan512Files() {
            Map<String, byte[]> bad = new LinkedHashMap<>();
            bad.put("bad/SKILL.md", utf8("---\nname: bad\ndescription: y\n---\nbody\n"));
            for (int i = 0; i < 512; i++) {
                bad.put("bad/references/f" + i + ".md", utf8("x"));
            }

            assertSkipped(bad);
        }

        @Test
        void acceptsExactly512Files() {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("wide/SKILL.md", utf8("---\nname: wide\ndescription: y\n---\nbody\n"));
            for (int i = 0; i < 511; i++) {
                files.put("wide/references/f" + i + ".md", utf8("x"));
            }

            assertEquals(512, new McpSkillCatalogue(files).skills().getFirst().files().size());
        }

        @Test
        void refusesMoreThan16MebibytesInTotal() {
            byte[] half = new byte[8 * 1024 * 1024];
            Arrays.fill(half, (byte) 'x');
            assertSkipped(Map.of(
                    "bad/SKILL.md", utf8("---\nname: bad\ndescription: y\n---\nbody\n"),
                    "bad/a.md", half,
                    "bad/b.md", half));
        }

        /** A self-referencing alias would build a cyclic document; no alias is allowed at all. */
        @Test
        void refusesARecursiveAlias() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\nx: &r [*r]\n---\nbody\n");
        }

        /** A JSON object has text keys only; {@code ~} is YAML's null. */
        @Test
        void refusesANullKey() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\n~: x\n---\nbody\n");
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\nnull: x\n---\nbody\n");
        }

        @Test
        void refusesANumericKey() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\n1: x\n---\nbody\n");
        }

        @Test
        void refusesABooleanKey() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\nTrue: x\n---\nbody\n");
        }

        @Test
        void refusesANonTextKeyInsideNestedMetadata() {
            assertSkipped("bad/SKILL.md", "---\nname: bad\ndescription: y\nmetadata:\n  1: x\n---\nbody\n");
        }

        /** None of the malformed ones escapes the constructor: each is a skip, logged. */
        @Test
        void neverThrowsOutOfTheConstructor() {
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("alias/SKILL.md", utf8("---\nname: alias\ndescription: y\nx: &r [*r]\n---\nbody\n"));
            files.put("nullkey/SKILL.md", utf8("---\nname: nullkey\ndescription: y\n~: x\n---\nbody\n"));
            files.put("numkey/SKILL.md", utf8("---\nname: numkey\ndescription: y\n1: x\n---\nbody\n"));

            assertEquals(List.of("good"), names(withGood(files)));
        }

        @Test
        void refusesAParentSegmentInAFilePath() {
            assertSkipped(Map.of(
                    "bad/SKILL.md", utf8("---\nname: bad\ndescription: y\n---\nbody\n"),
                    "bad/../x.md", utf8("x")));
        }

        @Test
        void refusesAnEmptySegmentInAFilePath() {
            assertSkipped(Map.of(
                    "bad/SKILL.md", utf8("---\nname: bad\ndescription: y\n---\nbody\n"),
                    "bad//x.md", utf8("x")));
        }
    }

    /**
     * The classpath scan itself, over a directory and over a jar: a file's path is taken relative to the
     * {@code mcp-skills/} root it was found under, so a directory inside a skill that happens to be named
     * {@code mcp-skills} is just a directory.
     */
    @Nested
    class Classpath {

        private static final String NESTED = "mcp-skills/good/mcp-skills/notes.md";

        private static final Map<String, String> TREE = Map.of(
                "mcp-skills/good/SKILL.md", GOOD,
                NESTED, "# Notes\n");

        private void assertServesTheNestedDirectory(McpSkillCatalogue catalogue) {
            assertEquals(List.of("good"), names(catalogue));
            assertEquals(List.of("skill://good/SKILL.md", "skill://good/mcp-skills/notes.md"),
                    catalogue.skill("skill://good/SKILL.md").files().stream().map(McpSkillFile::uri).toList());
            assertEquals("# Notes\n", catalogue.file("skill://good/mcp-skills/notes.md").orElseThrow().text());
        }

        @Test
        void readsADirectoryNamedMcpSkillsInsideASkillFromADirectory(@TempDir Path root) throws IOException {
            for (Map.Entry<String, String> entry : TREE.entrySet()) {
                Path file = root.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            }

            try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
                assertServesTheNestedDirectory(McpSkillCatalogue.fromClasspath(loader));
            }
        }

        @Test
        void readsADirectoryNamedMcpSkillsInsideASkillFromAJar(@TempDir Path dir) throws IOException {
            Path jar = dir.resolve("skills.jar");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                for (String directory : List.of("mcp-skills/", "mcp-skills/good/", "mcp-skills/good/mcp-skills/")) {
                    out.putNextEntry(new JarEntry(directory));
                    out.closeEntry();
                }
                for (Map.Entry<String, String> entry : new TreeMap<>(TREE).entrySet()) {
                    out.putNextEntry(new JarEntry(entry.getKey()));
                    out.write(utf8(entry.getValue()));
                    out.closeEntry();
                }
            }

            try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
                assertServesTheNestedDirectory(McpSkillCatalogue.fromClasspath(loader));
            }
        }
    }
}
