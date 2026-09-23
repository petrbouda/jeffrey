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

import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpMcpToolsTest {

    @Mock
    HeapDumpToolsDelegate delegate;

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
    void usesTheDefaultHistogramOrderForABlankArgumentThroughTheReflectiveAdapter(String sortBy) {
        HeapDumpMcpTools target = new HeapDumpMcpTools(delegate);

        String result = new ReflectiveToolset(target, "heap").call(
                "heap_getClassHistogram", Json.createObject().put("sortBy", sortBy));

        assertEquals(target.getClassHistogram(null, null), result);
        assertTrue(result.contains("by SIZE"), result);
    }

    @Test
    void propagatesDelegateFailuresAsToolErrors() {
        when(delegate.getSummary()).thenThrow(new IllegalStateException("broken heap index"));

        ToolExecutionException error = assertThrows(ToolExecutionException.class,
                () -> new HeapDumpMcpTools(delegate).getHeapSummary());

        assertTrue(error.getMessage().contains("broken heap index"), error.getMessage());
    }

    @Test
    void reportsAMissingInstanceAsAToolError() {
        when(delegate.getInstanceDetail(42, false)).thenReturn(null);

        ToolExecutionException error = assertThrows(ToolExecutionException.class,
                () -> new HeapDumpMcpTools(delegate).getInstanceDetail(42L));

        assertTrue(error.getMessage().contains("42"), error.getMessage());
    }

    @Test
    void keepsAnEmptyGcRootPathResultAsAnOrdinaryAnswer() {
        when(delegate.getPathsToGCRoot(42, true, 3)).thenReturn(List.of());

        String result = new HeapDumpMcpTools(delegate).getPathToGCRoot(42L, 3);

        assertTrue(result.startsWith("No paths to GC root found"), result);
    }
}
