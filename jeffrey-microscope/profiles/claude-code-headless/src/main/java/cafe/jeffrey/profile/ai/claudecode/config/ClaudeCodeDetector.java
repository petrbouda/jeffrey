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

package cafe.jeffrey.profile.ai.claudecode.config;

import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeCliClient;

import java.time.Duration;

/**
 * Detects whether the Claude Code CLI is installed on the host, independent of whether an AI provider is
 * configured. Exposed from the (exported) config package so deployments can offer to enable Claude Code
 * when it is present but unused. The detection (a {@code claude --version} probe) is memoized.
 */
public final class ClaudeCodeDetector {

    // The per-invocation timeout is irrelevant here — detection only runs the CLI's version probe, which
    // has its own internal timeout.
    private static final Duration UNUSED_INVOCATION_TIMEOUT = Duration.ofSeconds(60);

    private final ClaudeCodeCliClient cliClient;

    public ClaudeCodeDetector(String cliPath) {
        this.cliClient = new ClaudeCodeCliClient(cliPath, UNUSED_INVOCATION_TIMEOUT);
    }

    /**
     * True if the Claude Code CLI is installed and responds to {@code --version}. The result is memoized
     * for the lifetime of this detector.
     */
    public boolean isInstalled() {
        return cliClient.isAvailable();
    }
}
