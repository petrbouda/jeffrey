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

import cafe.jeffrey.shared.common.JeffreyVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/internal/version}: the running Microscope's version, which the UI's top bar
 * shows. The check for a newer release is {@link VersionUpdateCheckController}'s.
 */
@RestController
@RequestMapping("/api/internal")
public class VersionController {

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("version", JeffreyVersion.resolveJeffreyVersion());
    }
}
