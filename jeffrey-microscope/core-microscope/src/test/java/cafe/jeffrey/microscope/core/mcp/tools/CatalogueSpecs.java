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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.mcp.McpTestToolsets;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;

import java.util.ArrayList;
import java.util.List;

/**
 * The tool specs a family advertises, built the way the endpoint builds them, so a test can check the
 * next calls an answer hands back against the tools they name with {@code McpNextToolConformance}.
 */
public final class CatalogueSpecs {

    private CatalogueSpecs() {
    }

    /** A profile-scoped family: its schemas carry the synthetic {@code profileId}. */
    public static List<McpToolSpec> profileScoped(Class<?> tools, String family) {
        return McpTestToolsets.unscoped(tools, family, profileId -> {
            throw new UnsupportedOperationException("specs need no target");
        }).specs();
    }

    /** A family registered on one instance, such as {@code profiles_list} or the recordings tools. */
    public static List<McpToolSpec> served(Object tools, String family) {
        return new ReflectiveToolset(tools, family).specs();
    }

    @SafeVarargs
    public static List<McpToolSpec> of(List<McpToolSpec>... families) {
        List<McpToolSpec> specs = new ArrayList<>();
        for (List<McpToolSpec> family : families) {
            specs.addAll(family);
        }
        return List.copyOf(specs);
    }
}
