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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeToolsetTest {

    private final CompositeToolset toolset = new CompositeToolset(List.of(
            new ReflectiveToolset(new FirstTools(), "first"),
            new ReflectiveToolset(new SecondTools(), "second")));

    @Test
    void advertisesEveryMembersTools() {
        List<String> names = toolset.specs().stream().map(McpToolSpec::name).toList();
        assertTrue(names.contains("first_alpha"));
        assertTrue(names.contains("second_beta"));
    }

    @Test
    void routesACallToTheMemberThatOwnsTheTool() {
        assertEquals("alpha", toolset.call("first_alpha", Json.createObject()));
        assertEquals("beta", toolset.call("second_beta", Json.createObject()));
    }

    @Test
    void rejectsAnUnknownTool() {
        assertThrows(IllegalArgumentException.class,
                () -> toolset.call("third_gamma", Json.createObject()));
    }

    /**
     * Two families answering to one name would leave the model calling whichever was registered
     * first — a wiring mistake worth failing over rather than resolving by accident.
     */
    @Test
    void rejectsDuplicateToolNames() {
        assertThrows(IllegalStateException.class, () -> new CompositeToolset(List.of(
                new ReflectiveToolset(new FirstTools(), "same"),
                new ReflectiveToolset(new FirstTools(), "same"))));
    }

    @Test
    void acceptsNoMembersAtAll() {
        assertTrue(new CompositeToolset(List.of()).specs().isEmpty());
    }

    public static class FirstTools {

        @Tool(description = "First")
        public String alpha() {
            return "alpha";
        }
    }

    public static class SecondTools {

        @Tool(description = "Second")
        public String beta() {
            return "beta";
        }
    }
}
