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
import cafe.jeffrey.profile.ai.config.AiSettings;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects whether the Claude Code CLI is installed on the host, independent of whether an AI provider is
 * configured. Exposed from the (exported) config package so deployments can offer to enable Claude Code
 * when it is present but unused.
 * <p>
 * The CLI path is read on every call rather than captured, because it is a user-editable setting that
 * can change while the application runs. Probing costs a subprocess, so results are memoized — but keyed
 * <em>by path</em>: memoizing a single result would make a corrected path keep reporting the old verdict.
 * <p>
 * The path is resolved through {@link AiSettings} rather than read directly, so its default lives in
 * one place alongside the rest of the AI settings.
 */
public final class ClaudeCodeDetector {

    // The per-invocation timeout is irrelevant here — detection only runs the CLI's version probe, which
    // has its own internal timeout.
    private static final Duration UNUSED_INVOCATION_TIMEOUT = Duration.ofSeconds(60);

    private final SettingsStore settingsStore;
    private final Map<String, Boolean> probesByPath = new ConcurrentHashMap<>();

    public ClaudeCodeDetector(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    /**
     * True if the Claude Code CLI is installed at the currently configured path and responds to
     * {@code --version}. The verdict is memoized per path.
     */
    public boolean isInstalled() {
        String path = AiSettings.from(settingsStore).cliPath();
        return probesByPath.computeIfAbsent(path, ClaudeCodeDetector::probe);
    }

    private static boolean probe(String path) {
        return new ClaudeCodeCliClient(path, UNUSED_INVOCATION_TIMEOUT).isAvailable();
    }
}
