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

import cafe.jeffrey.microscope.core.mcp.tools.McpToolCatalogue;
import cafe.jeffrey.microscope.core.mcp.tools.NextCalls;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.mcp.McpToolWeight;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolCatalogueTest {

    @Nested
    class Coverage {

        /** A class the assembler registers and the catalogue forgot — or the other way — fails here. */
        @Test
        void namesExactlyTheAdvertisedTools() {
            Set<String> advertised = AdvertisedTools.all().stream()
                    .map(McpToolSpec::name)
                    .collect(Collectors.toCollection(TreeSet::new));

            assertEquals(advertised, McpToolCatalogue.tools());
        }

        /** The weight a next call carries is the one the advertised hints imply. */
        @Test
        void weighsEveryToolAsItsAdvertisedHintsDo() {
            for (McpToolSpec spec : AdvertisedTools.all()) {
                assertEquals(McpToolWeight.of(spec), McpToolCatalogue.weightOf(spec.name()), spec.name());
            }
        }

        /** The documents the agent reads whole are heavy; nothing else is. */
        @Test
        void theHeavyToolsAreTheLongDocuments() {
            Set<String> heavy = McpToolCatalogue.tools().stream()
                    .filter(tool -> McpToolCatalogue.weightOf(tool) == McpToolWeight.HEAVY)
                    .collect(Collectors.toCollection(TreeSet::new));

            assertEquals(new TreeSet<>(List.of(
                    "compare_flamegraph", "flamegraph_export", "heap_getDominatorTreeRoots", "jfr_executeQuery",
                    "jvm_threadDump", "traces_operationExport", "traces_operationFlamegraphExport",
                    "traces_spanFlamegraphExport", "traces_traceExport")), heavy);
        }
    }

    @Nested
    class Refuses {

        @Test
        void aToolNoClassDeclares() {
            assertThrows(IllegalArgumentException.class, () -> NextCalls.to("profiles_missing"));
        }
    }
}
