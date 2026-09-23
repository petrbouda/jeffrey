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
