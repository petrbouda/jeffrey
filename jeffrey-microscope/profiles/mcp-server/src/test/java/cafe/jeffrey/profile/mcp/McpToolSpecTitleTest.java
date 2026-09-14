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
