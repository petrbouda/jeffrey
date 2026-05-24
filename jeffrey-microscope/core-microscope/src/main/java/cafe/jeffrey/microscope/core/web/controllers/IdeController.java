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

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ide")
public class IdeController {

    private final IdeBridge ideBridge;

    public IdeController(IdeBridge ideBridge) {
        this.ideBridge = ideBridge;
    }

    @PostMapping("/open")
    public IdeOpenResponse open(@RequestBody IdeOpenRequest request) {
        IdeOpenResult result = ideBridge.open(request);
        return new IdeOpenResponse(result.success(), result.message());
    }

    @GetMapping("/source")
    public IdeSourceResponse source(
            @RequestParam(value = "profileId", required = false) String profileId,
            @RequestParam("fqn") String fqn,
            @RequestParam("method") String method) {
        IdeSourceResult result = ideBridge.fetchSource(new IdeSourceRequest(profileId, fqn, method));
        return new IdeSourceResponse(result.success(), result.content(), result.message());
    }

    @GetMapping("/targets")
    public IdeTargetsResult targets(
            @RequestParam(value = "profileId", required = false) String profileId,
            @RequestParam(value = "fqn", required = false) String fqn) {
        return ideBridge.discoverTargets(profileId, fqn);
    }

    @PostMapping("/target")
    public IdeTargetResponse selectTarget(@RequestBody IdeTargetRequest request) {
        boolean success = ideBridge.selectTarget(request.profileId(), request.port(), request.projectId());
        return new IdeTargetResponse(success);
    }

    public record IdeOpenResponse(boolean success, String message) {
    }

    public record IdeSourceResponse(boolean success, String content, String message) {
    }

    public record IdeTargetRequest(String profileId, int port, String projectId) {
    }

    public record IdeTargetResponse(boolean success) {
    }
}
