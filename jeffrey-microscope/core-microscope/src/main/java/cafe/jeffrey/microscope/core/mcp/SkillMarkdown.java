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

import cafe.jeffrey.shared.common.Json;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@code SKILL.md} split into its YAML frontmatter, rendered as JSON, and the body after it.
 * <p>
 * The frontmatter is read with SnakeYAML's {@link SafeConstructor}, which builds nothing but maps,
 * lists and scalars, and with a resolver narrowed to YAML 1.2's core schema: SnakeYAML reads YAML 1.1,
 * where {@code yes}, {@code on}, {@code off}, a date or {@code 0755} silently become a boolean, a
 * timestamp or an octal number. The skills extension asks for the frontmatter verbatim, and a host
 * compares it field by field with the file it reads, so a word the author wrote stays that word.
 * <p>
 * The parse is strict and runs once. Frontmatter that any YAML parser would reject — a plain value
 * holding {@code ": "} included, which the author quotes instead — is refused with an
 * {@link IllegalArgumentException}, which the catalogue turns into a skipped skill.
 *
 * @param frontmatter every key the author wrote, with its value
 * @param body        the text after the closing delimiter, leading blank lines removed
 */
record SkillMarkdown(ObjectNode frontmatter, String body) {

    private static final String DELIMITER = "---";
    private static final char NEWLINE = '\n';

    /**
     * No alias may stand for a collection: frontmatter has no use for one, and a self-referencing
     * alias would build a cyclic document that no JSON rendering can finish.
     */
    private static final int MAX_ALIASES = 0;

    private static final String NO_FRONTMATTER = "the file does not start with a --- frontmatter line";
    private static final String UNTERMINATED = "the frontmatter has no closing --- line";
    private static final String NOT_A_MAPPING = "the frontmatter is not a YAML mapping";
    private static final String NOT_YAML = "the frontmatter is not valid YAML: ";
    private static final String NON_TEXT_KEY = "the frontmatter has a key that is not text";
    private static final String NOT_JSON = "the frontmatter cannot be rendered as JSON: ";

    SkillMarkdown {
        frontmatter = frontmatter.deepCopy();
    }

    @Override
    public ObjectNode frontmatter() {
        return frontmatter.deepCopy();
    }

    /**
     * @throws IllegalArgumentException naming what is wrong: no frontmatter, no closing line, YAML
     *                                  that does not parse (a duplicate key included), or a document
     *                                  that is not a mapping
     */
    static SkillMarkdown parse(String text) {
        List<String> lines = text.lines().toList();
        if (lines.isEmpty() || !DELIMITER.equals(lines.getFirst().stripTrailing())) {
            throw new IllegalArgumentException(NO_FRONTMATTER);
        }
        int closing = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (DELIMITER.equals(lines.get(i).stripTrailing())) {
                closing = i;
                break;
            }
        }
        if (closing < 0) {
            throw new IllegalArgumentException(UNTERMINATED);
        }
        String yaml = String.join(String.valueOf(NEWLINE), lines.subList(1, closing));
        return new SkillMarkdown(frontmatter(yaml), bodyAfter(text, closing));
    }

    /** The text after the closing line, as written: only the blank lines that open it are removed. */
    private static String bodyAfter(String text, int closingLine) {
        int offset = 0;
        for (int line = 0; line <= closingLine; line++) {
            int end = text.indexOf(NEWLINE, offset);
            if (end < 0) {
                return "";
            }
            offset = end + 1;
        }
        return text.substring(offset).stripLeading();
    }

    private static ObjectNode frontmatter(String yaml) {
        Object document;
        try {
            document = parser().load(yaml);
        } catch (YAMLException e) {
            throw new IllegalArgumentException(NOT_YAML + e.getMessage(), e);
        }
        if (!(document instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(NOT_A_MAPPING);
        }
        requireTextKeys(document);
        JsonNode tree;
        try {
            tree = Json.toTree(document);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(NOT_JSON + e.getOriginalMessage(), e);
        }
        if (!(tree instanceof ObjectNode object)) {
            throw new IllegalArgumentException(NOT_A_MAPPING);
        }
        return object;
    }

    /**
     * A JSON object's keys are text, so a YAML key that is null, a number or a boolean — {@code ~: x},
     * {@code 1: x}, {@code True: x} — has no verbatim rendering, at any depth.
     */
    private static void requireTextKeys(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException(NON_TEXT_KEY);
                }
                requireTextKeys(entry.getValue());
            }
        } else if (node instanceof Collection<?> items) {
            for (Object item : items) {
                requireTextKeys(item);
            }
        }
    }

    /** One per parse: a {@link Yaml} instance is not thread-safe. */
    private static Yaml parser() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(MAX_ALIASES);
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options,
                new CoreSchemaResolver());
    }

    /**
     * YAML 1.2's core schema: {@code true}/{@code false}, decimal integers, floats and {@code null}
     * resolve to their types; everything else — YAML 1.1's yes/no/on/off, timestamps, sexagesimal and
     * leading-zero octal numbers, the {@code <<} merge key — stays a string.
     */
    private static final class CoreSchemaResolver extends Resolver {

        private static final Pattern CORE_BOOL = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
        private static final Pattern CORE_INT = Pattern.compile("^[-+]?(?:0|[1-9][0-9]*)$");
        private static final Pattern CORE_FLOAT =
                Pattern.compile("^[-+]?(?:\\.[0-9]+|[0-9]+\\.[0-9]*|[0-9]+(?=[eE]))(?:[eE][-+]?[0-9]+)?$");
        private static final Pattern CORE_NULL = Pattern.compile("^(?:~|null|Null|NULL)$");
        private static final Pattern EMPTY = Pattern.compile("^$");

        private static final String BOOL_FIRST = "tfTF";
        private static final String NUMBER_FIRST = "-+0123456789";
        private static final String FLOAT_FIRST = "-+0123456789.";
        private static final String NULL_FIRST = "~nN";

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, CORE_BOOL, BOOL_FIRST);
            addImplicitResolver(Tag.INT, CORE_INT, NUMBER_FIRST);
            addImplicitResolver(Tag.FLOAT, CORE_FLOAT, FLOAT_FIRST);
            addImplicitResolver(Tag.NULL, CORE_NULL, NULL_FIRST);
            addImplicitResolver(Tag.NULL, EMPTY, null);
        }
    }
}
