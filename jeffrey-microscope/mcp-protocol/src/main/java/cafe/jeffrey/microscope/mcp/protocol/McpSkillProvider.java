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

import java.util.List;
import java.util.Optional;

/**
 * The skills one endpoint serves over the {@code io.modelcontextprotocol/skills} extension: what
 * {@code skills/list} lists, what {@code skills/get} looks up, and the files {@code resources/read}
 * serves under {@code skill://}.
 * <p>
 * An endpoint holding {@link #NONE} declares no {@code skills} extension at {@code server/discover}.
 * The lookups default to a scan of {@link #skills()}; a provider with an index overrides them.
 */
public interface McpSkillProvider {

    /** No skills: the extension is not declared, and no URI names a skill. */
    McpSkillProvider NONE = List::of;

    /** The message a URI that names no served skill is refused with. */
    String UNKNOWN_SKILL = "No skill is served at %s; skills/list names the ones that are";

    /** Every skill served, sorted by URI; each entry carries its complete manifest. */
    List<McpSkill> skills();

    /**
     * Whether there is any skill to serve: what decides that the extension is declared, at
     * {@code server/discover} and wherever else the server describes itself.
     */
    default boolean servesAny() {
        return !skills().isEmpty();
    }

    /**
     * The skill whose {@code SKILL.md} is at this URI.
     *
     * @throws McpProtocolException {@code -32602} when no served skill has this URI — a supporting
     *                              file's URI included, since that names a file rather than a skill
     */
    default McpSkill skill(String uri) {
        return skills().stream()
                .filter(skill -> skill.uri().equals(uri))
                .findFirst()
                .orElseThrow(() -> new McpProtocolException(McpErrorCode.INVALID_PARAMS, UNKNOWN_SKILL.formatted(uri)));
    }

    /** Any file in any served skill's manifest, or empty when none is at this URI. */
    default Optional<McpSkillFile> file(String uri) {
        return skills().stream()
                .flatMap(skill -> skill.files().stream())
                .filter(file -> file.uri().equals(uri))
                .findFirst();
    }
}
