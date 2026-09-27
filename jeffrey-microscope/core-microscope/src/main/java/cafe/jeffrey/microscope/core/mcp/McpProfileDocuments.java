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

import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindings;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileSchema;

import java.util.function.Function;

/**
 * The two per-profile documents the resources serve that are not one tool's answer: the profile
 * database's schema and the findings the profile already holds. Each reads one profile, by id,
 * through the same profile lease the tools use.
 *
 * @param schemaOf   the database schema of the profile with that id
 * @param findingsOf the cached findings of the profile with that id; never computes them
 */
public record McpProfileDocuments(
        Function<String, ProfileSchema> schemaOf,
        Function<String, ProfileFindings> findingsOf) {

    public McpProfileDocuments {
        if (schemaOf == null) {
            throw new IllegalArgumentException("schemaOf must not be null");
        }
        if (findingsOf == null) {
            throw new IllegalArgumentException("findingsOf must not be null");
        }
    }
}
