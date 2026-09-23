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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A tool's display title is derived from its name rather than written beside every method, so the
 * derivation is the thing worth testing: it runs a hundred-odd times and nobody reads the output.
 */
class McpToolSpecTitleTest {

    private static String titleOf(String name) {
        return new McpToolSpec(name, "description", Json.createObject(), McpToolAnnotations.READ_ONLY).title();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "profiles_list,               'Profiles: List'",
            "profiles_samplerHealth,      'Profiles: Sampler Health'",
            "traces_spanFlamegraphExport, 'Traces: Span Flamegraph Export'",
            "heap_getClassHistogram,      'Heap: Get Class Histogram'",
            "timeline_hotWindows,         'Timeline: Hot Windows'",
            "operations_cancel,           'Operations: Cancel'"
    })
    void readsAToolNameAsWords(String name, String expected) {
        assertEquals(expected, titleOf(name));
    }

    /** A family that is an acronym or two words is spelled, not capitalised by rule. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "jfr_executeQuery,       'JFR: Execute Query'",
            "jvm_gcDetail,           'JVM: GC Detail'",
            "jdbc_pools,             'JDBC: Pools'",
            "grpc_overview,          'gRPC: Overview'",
            "http_endpoint,          'HTTP: Endpoint'",
            "io_slowest,             'I/O: Slowest'",
            "ide_resolve,            'IDE: Resolve'",
            "methodtracing_overview, 'Method tracing: Overview'"
    })
    void spellsAFamilyTheWayItIsRead(String name, String expected) {
        assertEquals(expected, titleOf(name));
    }

    /** A run of capitals stays one word; the word after it starts a new one. */
    @Test
    void keepsAnAcronymInTheMethodNameTogether() {
        assertEquals("Heap: Get Path To GC Root", titleOf("heap_getPathToGCRoot"));
        assertEquals("Heap: OQL", titleOf("heap_oql"));
        assertEquals("JVM: NMT", titleOf("jvm_nmt"));
    }

    /** A name with no family prefix is still given something readable rather than left blank. */
    @Test
    void handlesANameWithNoPrefix() {
        assertEquals("Ping", titleOf("ping"));
        assertEquals("Trailing_", titleOf("trailing_"));
    }
}
