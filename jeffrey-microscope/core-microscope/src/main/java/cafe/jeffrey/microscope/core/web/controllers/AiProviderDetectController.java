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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.dto.response.ClaudeCodeDetectResponse;
import cafe.jeffrey.profile.ai.claudecode.config.ClaudeCodeDetector;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

/**
 * Reports whether the Claude Code CLI is installed while no AI provider is configured — so the UI can
 * offer to enable it. Returns {@code 204 No Content} when there is nothing to prompt (AI already
 * configured, or Claude Code not detected), mirroring the update-check endpoint so the frontend stays
 * trivial.
 * <p>
 * The configured provider is read per request rather than captured at startup, so the prompt stops
 * appearing as soon as a provider is selected.
 */
@RestController
@RequestMapping("/api/internal/ai")
public class AiProviderDetectController {

    private final ClaudeCodeDetector claudeCodeDetector;
    private final SettingsStore settingsStore;

    public AiProviderDetectController(ClaudeCodeDetector claudeCodeDetector, SettingsStore settingsStore) {
        this.claudeCodeDetector = claudeCodeDetector;
        this.settingsStore = settingsStore;
    }

    @GetMapping("/claude-code-detect")
    public ResponseEntity<ClaudeCodeDetectResponse> detect() {
        if (isAiConfigured() || !claudeCodeDetector.isInstalled()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new ClaudeCodeDetectResponse(false, true));
    }

    private boolean isAiConfigured() {
        String provider = settingsStore.getString(
                MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE);
        return !MicroscopeSettingKeys.PROVIDER_NONE.equals(provider);
    }
}
