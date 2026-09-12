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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.mcp.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpMcpToolsTest {

    @Mock
    HeapDumpToolsDelegate delegate;

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
