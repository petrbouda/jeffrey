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
import cafe.jeffrey.microscope.core.manager.GitHubReleaseChecker;
import cafe.jeffrey.shared.common.JeffreyVersion;

/**
 * Microscope-only WorkspaceBrowser endpoint: checks GitHub for a newer release. The shared
 * {@code /api/internal/version} endpoint lives in {@code cafe.jeffrey.shared.ui.version}; only
 * microscope offers the update check, so it stays here.
 */
@RestController
@RequestMapping("/api/internal")
public class VersionUpdateCheckController {

    private final GitHubReleaseChecker gitHubReleaseChecker;

    public VersionUpdateCheckController(GitHubReleaseChecker gitHubReleaseChecker) {
        this.gitHubReleaseChecker = gitHubReleaseChecker;
    }

    @GetMapping("/version/update-check")
    public ResponseEntity<?> updateCheck() {
        return gitHubReleaseChecker.check(JeffreyVersion.resolveJeffreyVersion())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
