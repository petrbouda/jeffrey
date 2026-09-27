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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeToolsetTest {

    private final CompositeToolset toolset = new CompositeToolset(List.of(
            StubToolset.of(StubToolset.answering("first_alpha", "alpha")),
            StubToolset.of(StubToolset.answering("second_beta", "beta"))));

    @Test
    void advertisesEveryMembersTools() {
        List<String> names = toolset.specs().stream().map(McpToolSpec::name).toList();
        assertTrue(names.contains("first_alpha"));
        assertTrue(names.contains("second_beta"));
    }

    @Test
    void routesACallToTheMemberThatOwnsTheTool() {
        assertEquals("alpha", toolset.call("first_alpha", McpJson.createObject()));
        assertEquals("beta", toolset.call("second_beta", McpJson.createObject()));
    }

    @Test
    void rejectsAnUnknownTool() {
        assertThrows(UnknownToolException.class,
                () -> toolset.call("third_gamma", McpJson.createObject()));
    }

    /**
     * Two families answering to one name would leave the model calling whichever was registered
     * first — a wiring mistake worth failing over rather than resolving by accident.
     */
    @Test
    void rejectsDuplicateToolNames() {
        assertThrows(IllegalStateException.class, () -> new CompositeToolset(List.of(
                StubToolset.of(StubToolset.answering("same_alpha", "alpha")),
                StubToolset.of(StubToolset.answering("same_alpha", "alpha")))));
    }

    @Test
    void acceptsNoMembersAtAll() {
        assertTrue(new CompositeToolset(List.of()).specs().isEmpty());
    }
}
