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

package cafe.jeffrey.performance.analyst.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.performance.analyst.recommendations.RepoAnalysisTools;
import cafe.jeffrey.profile.ai.chat.McpToolset;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoToolsTest {

    private static final String BASE_URL = "http://127.0.0.1:8080/api/internal/mcp/claude-code";

    @Nested
    class Factory {

        @Test
        void buildsRunScopedUrlAndWildcardAllowedTools() {
            McpToolset toolset = new RepoToolsetFactory(BASE_URL).forRun("run-123");

            assertEquals(RepoToolsetFactory.SERVER_NAME, toolset.serverName());
            assertEquals(BASE_URL + "?runId=run-123", toolset.url());
            assertEquals("mcp__" + RepoToolsetFactory.SERVER_NAME + "__*", toolset.allowedTools().get(0));
        }

        @Test
        void encodesRunIdIntoQuery() {
            McpToolset toolset = new RepoToolsetFactory(BASE_URL).forRun("a b/c");

            assertTrue(toolset.url().endsWith("?runId=a+b%2Fc"), toolset.url());
        }
    }

    @Nested
    class Registry {

        @TempDir
        Path repoRoot;

        @Test
        void resolvesRegisteredToolsAndForgetsOnUnregister() {
            RepoToolsRegistry registry = new RepoToolsRegistry();
            RepoAnalysisTools tools = new RepoAnalysisTools(repoRoot);

            registry.register("run-1", tools);
            assertSame(tools, registry.resolve("run-1"));

            registry.unregister("run-1");
            assertThrows(IllegalArgumentException.class, () -> registry.resolve("run-1"));
        }

        @Test
        void unknownRunThrows() {
            assertThrows(IllegalArgumentException.class, () -> new RepoToolsRegistry().resolve("nope"));
        }
    }
}
