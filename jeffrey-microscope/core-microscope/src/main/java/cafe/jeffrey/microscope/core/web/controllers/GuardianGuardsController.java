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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.manager.GuardianGuardsManager;
import cafe.jeffrey.microscope.persistence.api.GuardianGuard;

import java.time.Instant;
import java.util.List;

/**
 * Global CRUD API for managing the central Guardian guard definitions. Lives at the (non-profile)
 * platform path because guard configuration is shared across all profiles.
 */
@RestController
@RequestMapping("/api/internal/guardian/guards")
public class GuardianGuardsController {

    private static final Logger LOG = LoggerFactory.getLogger(GuardianGuardsController.class);

    private final GuardianGuardsManager manager;

    public GuardianGuardsController(GuardianGuardsManager manager) {
        this.manager = manager;
    }

    /** Editable fields of a guard. {@code matcherSpec}/{@code preconditions} carry JSON as text. */
    public record GuardRequest(
            String name,
            boolean enabled,
            String groupKind,
            String category,
            String resultType,
            String targetFrame,
            String matchingType,
            double infoThreshold,
            double warningThreshold,
            String matcherSpec,
            String preconditions,
            String summaryNoun,
            String explanation,
            String solution) {
    }

    public record GuardResponse(
            String guardId,
            String name,
            boolean enabled,
            boolean builtIn,
            String groupKind,
            String category,
            String resultType,
            String targetFrame,
            String matchingType,
            double infoThreshold,
            double warningThreshold,
            String matcherSpec,
            String preconditions,
            String summaryNoun,
            String explanation,
            String solution,
            Long createdAt) {

        static GuardResponse from(GuardianGuard guard) {
            Instant createdAt = guard.createdAt();
            return new GuardResponse(
                    guard.guardId(), guard.name(), guard.enabled(), guard.builtIn(), guard.groupKind(),
                    guard.category(), guard.resultType(), guard.targetFrame(), guard.matchingType(),
                    guard.infoThreshold(), guard.warningThreshold(), guard.matcherSpec(), guard.preconditions(),
                    guard.summaryNoun(), guard.explanation(), guard.solution(),
                    createdAt != null ? createdAt.toEpochMilli() : null);
        }
    }

    @GetMapping
    public List<GuardResponse> list() {
        return manager.list().stream().map(GuardResponse::from).toList();
    }

    @GetMapping("/{guardId}")
    public ResponseEntity<GuardResponse> get(@PathVariable("guardId") String guardId) {
        return manager.find(guardId)
                .map(guard -> ResponseEntity.ok(GuardResponse.from(guard)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public GuardResponse create(@RequestBody GuardRequest request) {
        GuardianGuard created = manager.create(toDraft(request));
        LOG.debug("Created guardian guard: guard_id={} name={}", created.guardId(), created.name());
        return GuardResponse.from(created);
    }

    @PutMapping("/{guardId}")
    public ResponseEntity<GuardResponse> update(@PathVariable("guardId") String guardId, @RequestBody GuardRequest request) {
        return manager.update(guardId, toDraft(request))
                .map(guard -> ResponseEntity.ok(GuardResponse.from(guard)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{guardId}")
    public ResponseEntity<Void> delete(@PathVariable("guardId") String guardId) {
        if (manager.delete(guardId)) {
            LOG.debug("Deleted guardian guard: guard_id={}", guardId);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /** Maps a request into a draft row; identity fields (id, builtIn, createdAt) are set by the manager. */
    private static GuardianGuard toDraft(GuardRequest request) {
        return new GuardianGuard(
                null,
                request.name(),
                request.enabled(),
                false,
                request.groupKind(),
                request.category(),
                request.resultType(),
                request.targetFrame(),
                request.matchingType(),
                request.infoThreshold(),
                request.warningThreshold(),
                request.matcherSpec(),
                request.preconditions(),
                request.summaryNoun(),
                request.explanation(),
                request.solution(),
                null);
    }
}
