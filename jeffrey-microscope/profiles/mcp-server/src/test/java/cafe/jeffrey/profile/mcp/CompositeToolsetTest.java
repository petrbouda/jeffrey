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
