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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One skill as the {@code io.modelcontextprotocol/skills} extension describes it: the {@code Skill}
 * entry {@code skills/list} and {@code skills/get} both return.
 * <p>
 * The manifest is complete — {@code SKILL.md} and every supporting file, each once, all inside the
 * skill's own directory — which is why this server never needs {@code resources/directory/read}: a
 * host holding the entry already knows every file it may read. The skill's name is the last segment
 * of its path, so a host can read it off the URI without fetching anything.
 *
 * @param uri         the URI of the skill's {@code SKILL.md}, {@code skill://<name>/SKILL.md}
 * @param frontmatter the {@code SKILL.md} YAML frontmatter as JSON, every key the author wrote
 * @param files       the complete manifest, {@code SKILL.md} included
 */
public record McpSkill(String uri, ObjectNode frontmatter, List<McpSkillFile> files) {

    /** The scheme the specification recommends, and the only one this server serves skills under. */
    public static final String SCHEME = "skill://";

    /** The file every skill has at its root, and the last segment of every skill URI. */
    public static final String SKILL_FILE = "SKILL.md";

    private static final String PATH_SEPARATOR = "/";
    private static final String SKILL_FILE_SUFFIX = PATH_SEPARATOR + SKILL_FILE;

    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_URI = "uri";
    private static final String FIELD_FRONTMATTER = "frontmatter";
    private static final String FIELD_RESOURCES = "resources";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_SIZE = "size";

    public McpSkill {
        if (uri == null || !uri.endsWith(SKILL_FILE_SUFFIX)) {
            throw new IllegalArgumentException("A skill is addressed by the URI of its " + SKILL_FILE + ": " + uri);
        }
        if (frontmatter == null) {
            throw new IllegalArgumentException("A skill needs its frontmatter: " + uri);
        }
        frontmatter = frontmatter.deepCopy();
        String name = requireText(frontmatter, FIELD_NAME, uri);
        requireText(frontmatter, FIELD_DESCRIPTION, uri);
        String root = uri.substring(0, uri.length() - SKILL_FILE_SUFFIX.length());
        if (!root.endsWith(PATH_SEPARATOR + name)) {
            throw new IllegalArgumentException(
                    "The last path segment of a skill's URI must be its name: uri=" + uri + " name=" + name);
        }
        if (files == null) {
            throw new IllegalArgumentException("A skill needs its manifest: " + uri);
        }
        files = List.copyOf(files);
        requireCompleteManifest(uri, root + PATH_SEPARATOR, files);
    }

    @Override
    public ObjectNode frontmatter() {
        return frontmatter.deepCopy();
    }

    /** The skill's {@code name}: a label, equal to the last segment of its path. */
    public String name() {
        return frontmatter.get(FIELD_NAME).asString();
    }

    public String description() {
        return frontmatter.get(FIELD_DESCRIPTION).asString();
    }

    /** The entry: {@code uri}, {@code frontmatter}, and {@code resources} as {@code {uri, digest, size}}. */
    public ObjectNode toJson() {
        ObjectNode entry = McpJson.createObject();
        entry.put(FIELD_URI, uri);
        entry.set(FIELD_FRONTMATTER, frontmatter.deepCopy());
        ArrayNode resources = entry.putArray(FIELD_RESOURCES);
        for (McpSkillFile file : files) {
            resources.addObject()
                    .put(FIELD_URI, file.uri())
                    .put(FIELD_DIGEST, file.digest())
                    .put(FIELD_SIZE, file.size());
        }
        return entry;
    }

    private static String requireText(ObjectNode frontmatter, String field, String uri) {
        JsonNode value = frontmatter.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException("A skill's frontmatter needs a " + field + ": " + uri);
        }
        return value.asString();
    }

    private static void requireCompleteManifest(String uri, String directory, List<McpSkillFile> files) {
        Set<String> seen = new HashSet<>();
        for (McpSkillFile file : files) {
            if (!file.uri().startsWith(directory)) {
                throw new IllegalArgumentException("A skill lists only files in its own directory: skill=" + uri
                        + " file=" + file.uri());
            }
            if (!seen.add(file.uri())) {
                throw new IllegalArgumentException("A skill lists each file once: " + file.uri());
            }
        }
        if (!seen.contains(uri)) {
            throw new IllegalArgumentException("A skill's manifest lists its own " + SKILL_FILE + ": " + uri);
        }
    }
}
