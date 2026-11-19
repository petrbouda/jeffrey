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
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeSourceResult;
import cafe.jeffrey.microscope.core.manager.ide.IdeTarget;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetStatus;
import cafe.jeffrey.microscope.core.manager.ide.IdeTargetsResult;
import org.springframework.web.bind.annotation.DeleteMapping;
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
        return new IdeOpenResponse(result.success(), result.message(), result.reason().name());
    }

    @GetMapping("/source")
    public IdeSourceResponse source(
            @RequestParam(value = "profileId", required = false) String profileId,
            @RequestParam("fqn") String fqn,
            @RequestParam(value = "method", required = false) String method) {
        IdeSourceResult result = ideBridge.fetchSource(new IdeSourceRequest(profileId, fqn, method));
        return new IdeSourceResponse(result.success(), result.content(), result.message(), result.decompiled());
    }

    @GetMapping("/targets")
    public IdeTargetsResult targets(
            @RequestParam(value = "profileId", required = false) String profileId,
            @RequestParam(value = "fqn", required = false) String fqn) {
        return ideBridge.discoverTargets(profileId, fqn);
    }

    @GetMapping("/has")
    public IdeHasResponse has(
            @RequestParam(value = "profileId", required = false) String profileId,
            @RequestParam("fqn") String fqn) {
        return new IdeHasResponse(ideBridge.hasClass(profileId, fqn));
    }

    @GetMapping("/status")
    public IdeTargetStatus status(@RequestParam(value = "profileId", required = false) String profileId) {
        return ideBridge.targetStatus(profileId);
    }

    @PostMapping("/target")
    public IdeTargetResponse selectTarget(@RequestBody IdeTargetRequest request) {
        IdeTarget target = new IdeTarget(
                request.port(),
                request.projectId(),
                request.ideName(),
                request.projectName(),
                request.basePath(),
                request.pid());
        boolean success = ideBridge.selectTarget(request.profileId(), target);
        return new IdeTargetResponse(success);
    }

    @DeleteMapping("/target")
    public IdeTargetResponse clearTarget(@RequestParam("profileId") String profileId) {
        return new IdeTargetResponse(ideBridge.clearTarget(profileId));
    }

    public record IdeOpenResponse(boolean success, String message, String reason) {
    }

    public record IdeSourceResponse(boolean success, String content, String message, boolean decompiled) {
    }

    public record IdeHasResponse(boolean found) {
    }

    /**
     * The window the reader picked. {@code basePath} travels with it because the link is what later
     * decides which directory on disk this profile is allowed to be read against — the picker already
     * shows it, so the choice and its consequence stay one act rather than a later lookup.
     */
    public record IdeTargetRequest(
            String profileId,
            int port,
            String projectId,
            String ideName,
            String projectName,
            String basePath,
            long pid) {
    }

    public record IdeTargetResponse(boolean success) {
    }
}
