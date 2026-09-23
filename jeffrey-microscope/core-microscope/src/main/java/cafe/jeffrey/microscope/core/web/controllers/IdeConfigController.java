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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/config/ide")
public class IdeConfigController {

    private final IdeBridge ideBridge;

    public IdeConfigController(IdeBridge ideBridge) {
        this.ideBridge = ideBridge;
    }

    @GetMapping
    public IdeConfigResponse get() {
        return new IdeConfigResponse(ideBridge.isEnabled(), ideBridge.mode().propertyValue());
    }

    public record IdeConfigResponse(boolean enabled, String mode) {
    }
}
