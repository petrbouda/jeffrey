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

package cafe.jeffrey.profile.ai.claudecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ClaudeCodeCliClient} against a stub {@code claude} script that emits canned
 * {@code stream-json}. Validates process invocation, stdin handling, output parsing, and the
 * availability probe without requiring a real Claude Code installation.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class ClaudeCodeCliClientTest {

    private static final String STUB_SCRIPT = """
            #!/bin/sh
            if [ "$1" = "--version" ]; then
              echo "2.1.0 (Claude Code)"
              exit 0
            fi
            cat > /dev/null
            echo '{"type":"system","subtype":"init"}'
            echo '{"type":"assistant","message":{"content":[{"type":"tool_use","name":"mcp__jeffrey__jfr_listEventTypes"}]}}'
            echo '{"type":"result","subtype":"success","is_error":false,"result":"FAKE_OK"}'
            """;

    @Test
    void reportsAvailableWhenCliRespondsToVersion(@TempDir Path dir) throws IOException {
        ClaudeCodeCliClient client = new ClaudeCodeCliClient(stubCli(dir), Duration.ofSeconds(30));
        assertTrue(client.isAvailable());
    }

    @Test
    void reportsUnavailableWhenCliMissing() {
        ClaudeCodeCliClient client = new ClaudeCodeCliClient("/nonexistent/claude-binary", Duration.ofSeconds(30));
        assertFalse(client.isAvailable());
    }

    @Test
    void runParsesResultTextAndToolUsage(@TempDir Path dir) throws IOException {
        ClaudeCodeCliClient client = new ClaudeCodeCliClient(stubCli(dir), Duration.ofSeconds(30));

        ClaudeCodeResult result = client.run(
                ClaudeCodeRequest.promptOnly("What are the hottest methods?", "You are a JFR analyst.", "sonnet"));

        assertFalse(result.error());
        assertEquals("FAKE_OK", result.text());
        assertEquals(List.of("mcp__jeffrey__jfr_listEventTypes"), result.toolsUsed());
    }

    private static String stubCli(Path dir) throws IOException {
        Path script = dir.resolve("claude-stub.sh");
        Files.writeString(script, STUB_SCRIPT);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script.toString();
    }
}
