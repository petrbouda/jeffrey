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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Claude Code CLI in headless ({@code --print}) mode. Authentication uses
 * the host's logged-in Claude subscription (or {@code ANTHROPIC_API_KEY} when set), so no API key is
 * managed by Jeffrey.
 * <p>
 * Each invocation runs as a short-lived subprocess. The prompt is written on stdin; the system
 * prompt and any MCP configuration are passed via temporary files in a per-call scratch directory
 * that is deleted on completion. Output is requested as {@code stream-json}, from which the final
 * assistant text and the names of any invoked tools are extracted.
 */
public final class ClaudeCodeCliClient {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeCodeCliClient.class);

    private static final String FLAG_PRINT = "--print";
    private static final String FLAG_OUTPUT_FORMAT = "--output-format";
    private static final String OUTPUT_FORMAT_STREAM_JSON = "stream-json";
    private static final String FLAG_VERBOSE = "--verbose";
    private static final String FLAG_MODEL = "--model";
    private static final String FLAG_APPEND_SYSTEM_PROMPT_FILE = "--append-system-prompt-file";
    private static final String FLAG_MCP_CONFIG = "--mcp-config";
    private static final String FLAG_STRICT_MCP_CONFIG = "--strict-mcp-config";
    private static final String FLAG_ALLOWED_TOOLS = "--allowed-tools";
    private static final String FLAG_VERSION = "--version";

    private static final String SCRATCH_DIR_PREFIX = "jeffrey-claude-code-";
    private static final String SYSTEM_PROMPT_FILE = "system-prompt.txt";
    private static final String MCP_CONFIG_FILE = "mcp-config.json";
    private static final Duration VERSION_CHECK_TIMEOUT = Duration.ofSeconds(15);

    private final String cliPath;
    private final Duration timeout;
    private final ClaudeCodeOutputParser outputParser = new ClaudeCodeOutputParser(new ObjectMapper());

    private volatile Boolean available;

    public ClaudeCodeCliClient(String cliPath, Duration timeout) {
        this.cliPath = (cliPath == null || cliPath.isBlank()) ? "claude" : cliPath.trim();
        this.timeout = timeout;
    }

    /**
     * Whether the CLI binary is present and runnable. The result is memoized for the lifetime of the
     * client; a configuration change requires an application restart (consistent with the rest of the
     * AI settings, which are read at startup).
     */
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (available == null) {
                available = checkVersion();
            }
            return available;
        }
    }

    private boolean checkVersion() {
        try {
            Process process = new ProcessBuilder(cliPath, FLAG_VERSION)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(VERSION_CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.warn("Claude Code version check timed out: cli_path={}", cliPath);
                return false;
            }
            boolean ok = process.exitValue() == 0;
            if (!ok) {
                LOG.warn("Claude Code version check failed: cli_path={} exit_code={}", cliPath, process.exitValue());
            }
            return ok;
        } catch (IOException e) {
            LOG.warn("Claude Code CLI not found or not executable: cli_path={} message={}", cliPath, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Run a single headless invocation and return the parsed result.
     */
    public ClaudeCodeResult run(ClaudeCodeRequest request) {
        Path scratchDir = null;
        try {
            scratchDir = Files.createTempDirectory(SCRATCH_DIR_PREFIX);
            List<String> command = buildCommand(request, scratchDir);

            Path stdoutFile = scratchDir.resolve("stdout.jsonl");
            Path stderrFile = scratchDir.resolve("stderr.log");

            Process process = new ProcessBuilder(command)
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
                    .start();

            writePrompt(process, request.prompt());

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOG.warn("Claude Code invocation timed out: timeout_in_sec={}", timeout.toSeconds());
                return ClaudeCodeResult.failure("Claude Code timed out after " + timeout.toSeconds() + " seconds.");
            }

            int exitCode = process.exitValue();
            List<String> lines = Files.readAllLines(stdoutFile, StandardCharsets.UTF_8);
            ClaudeCodeResult parsed = outputParser.parse(lines);

            if (exitCode != 0 && parsed.text().isBlank()) {
                String stderr = readStderr(stderrFile);
                LOG.warn("Claude Code invocation failed: exit_code={} stderr={}", exitCode, stderr);
                return ClaudeCodeResult.failure("Claude Code exited with code " + exitCode
                        + (stderr.isBlank() ? "" : ": " + stderr));
            }
            return parsed;
        } catch (IOException e) {
            LOG.error("Failed to invoke Claude Code CLI: message={}", e.getMessage(), e);
            return ClaudeCodeResult.failure("Failed to invoke Claude Code: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ClaudeCodeResult.failure("Claude Code invocation was interrupted.");
        } finally {
            deleteQuietly(scratchDir);
        }
    }

    private List<String> buildCommand(ClaudeCodeRequest request, Path scratchDir) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(cliPath);
        command.add(FLAG_PRINT);
        command.add(FLAG_OUTPUT_FORMAT);
        command.add(OUTPUT_FORMAT_STREAM_JSON);
        command.add(FLAG_VERBOSE);

        if (request.model() != null && !request.model().isBlank()) {
            command.add(FLAG_MODEL);
            command.add(request.model().trim());
        }

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            Path systemPromptFile = scratchDir.resolve(SYSTEM_PROMPT_FILE);
            Files.writeString(systemPromptFile, request.systemPrompt(), StandardCharsets.UTF_8);
            command.add(FLAG_APPEND_SYSTEM_PROMPT_FILE);
            command.add(systemPromptFile.toString());
        }

        if (request.hasMcpConfig()) {
            Path mcpConfigFile = scratchDir.resolve(MCP_CONFIG_FILE);
            Files.writeString(mcpConfigFile, request.mcpConfigJson(), StandardCharsets.UTF_8);
            command.add(FLAG_STRICT_MCP_CONFIG);
            command.add(FLAG_MCP_CONFIG);
            command.add(mcpConfigFile.toString());
            if (!request.allowedTools().isEmpty()) {
                command.add(FLAG_ALLOWED_TOOLS);
                command.add(String.join(",", request.allowedTools()));
            }
        }

        return command;
    }

    private static void writePrompt(Process process, String prompt) throws IOException {
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
    }

    private static String readStderr(Path stderrFile) {
        try {
            return Files.readString(stderrFile, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "";
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            LOG.debug("Failed to delete scratch file: path={} message={}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.debug("Failed to clean up scratch directory: dir={} message={}", dir, e.getMessage());
        }
    }
}
