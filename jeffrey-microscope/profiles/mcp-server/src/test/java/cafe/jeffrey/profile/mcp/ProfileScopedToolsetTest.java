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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileScopedToolsetTest {

    private final List<String> resolvedProfileIds = new ArrayList<>();

    private final ProfileScopedToolset<SampleTools> toolset = new ProfileScopedToolset<>(
            SampleTools.class,
            "sample",
            profileId -> {
                resolvedProfileIds.add(profileId);
                return new SampleTools(profileId);
            });

    @Nested
    class Schema {

        @Test
        void addsProfileIdToEveryTool() {
            for (McpToolSpec spec : toolset.specs()) {
                ObjectNode properties = (ObjectNode) spec.inputSchema().get("properties");
                assertTrue(properties.has(ProfileScopedToolset.PROFILE_ID_ARGUMENT),
                        "missing profileId on " + spec.name());
                assertEquals("string",
                        properties.get(ProfileScopedToolset.PROFILE_ID_ARGUMENT).get("type").asString());
            }
        }

        @Test
        void marksProfileIdRequiredAndNothingElse() {
            McpToolSpec spec = specOf("sample_describe");
            ArrayNode required = (ArrayNode) spec.inputSchema().get("required");
            assertEquals(1, required.size());
            assertEquals(ProfileScopedToolset.PROFILE_ID_ARGUMENT, required.get(0).asString());
        }

        @Test
        void keepsTheMethodsOwnParameters() {
            ObjectNode properties = (ObjectNode) specOf("sample_describe").inputSchema().get("properties");
            assertTrue(properties.has("suffix"));
            assertEquals("string", properties.get("suffix").get("type").asString());
        }

        @Test
        void prefixesToolNames() {
            List<String> names = toolset.specs().stream().map(McpToolSpec::name).toList();
            assertTrue(names.contains("sample_describe"));
            assertEquals("sample", toolset.prefix());
        }
    }

    @Nested
    class Dispatch {

        @Test
        void resolvesTheTargetFromTheProfileIdArgument() {
            String result = toolset.call("sample_describe", Json.createObject()
                    .put(ProfileScopedToolset.PROFILE_ID_ARGUMENT, "profile-1")
                    .put("suffix", "!"));

            assertEquals("profile-1!", result);
            assertEquals(List.of("profile-1"), resolvedProfileIds);
        }

        @Test
        void resolvesAgainForADifferentProfile() {
            toolset.call("sample_describe", arguments("profile-1"));
            toolset.call("sample_describe", arguments("profile-2"));

            assertEquals(List.of("profile-1", "profile-2"), resolvedProfileIds);
        }

        @Test
        void rejectsAMissingProfileId() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> toolset.call("sample_describe", Json.createObject().put("suffix", "!")));
            assertTrue(e.getMessage().contains(ProfileScopedToolset.PROFILE_ID_ARGUMENT));
        }

        @Test
        void rejectsABlankProfileId() {
            assertThrows(IllegalArgumentException.class,
                    () -> toolset.call("sample_describe", arguments("   ")));
        }

        /**
         * A blank id must fail before the resolver runs: resolving one would either open the wrong
         * profile or throw an error naming a lookup the caller never asked for.
         */
        @Test
        void doesNotResolveWhenTheProfileIdIsMissing() {
            assertThrows(IllegalArgumentException.class,
                    () -> toolset.call("sample_describe", Json.createObject()));
            assertTrue(resolvedProfileIds.isEmpty());
        }

        @Test
        void rejectsAnUnknownTool() {
            assertThrows(IllegalArgumentException.class,
                    () -> toolset.call("sample_missing", arguments("profile-1")));
        }
    }

    private static ObjectNode arguments(String profileId) {
        return Json.createObject().put(ProfileScopedToolset.PROFILE_ID_ARGUMENT, profileId);
    }

    private McpToolSpec specOf(String name) {
        return toolset.specs().stream()
                .filter(spec -> spec.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Stands in for a real profile-scoped tool class: constructed against one profile, then asked a
     * question that proves which one it was built for.
     */
    public static class SampleTools {

        private final String profileId;

        SampleTools(String profileId) {
            this.profileId = profileId;
        }

        @Tool(description = "Describe the profile this toolset was resolved for")
        public String describe(@ToolParam(description = "appended to the answer") String suffix) {
            return profileId + (suffix == null ? "" : suffix);
        }
    }
}
