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
import cafe.jeffrey.microscope.core.manager.advisor.AdvisorProjectFoldersManager;
import cafe.jeffrey.microscope.core.manager.advisor.ProjectFolderDraft;
import cafe.jeffrey.microscope.core.manager.advisor.ProjectFolderStatus;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;

import java.util.List;

/**
 * Global CRUD API for the named working copies the Advisor may read. Lives at the (non-profile)
 * platform path because the list is installation-wide: it is managed once in Settings → Advisor and
 * picked from when the Advisor runs on any profile.
 */
@RestController
@RequestMapping("/api/internal/advisor/project-folders")
public class AdvisorProjectFoldersController {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorProjectFoldersController.class);

    private final AdvisorProjectFoldersManager manager;

    public AdvisorProjectFoldersController(AdvisorProjectFoldersManager manager) {
        this.manager = manager;
    }

    /** The editable fields of a folder; the identity fields are assigned by the manager. */
    public record ProjectFolderRequest(String name, String path) {
    }

    /**
     * @param present whether the path resolves to a folder on this machine right now — a stale entry
     *                stays in the list and is reported as missing rather than dropped
     */
    public record ProjectFolderResponse(
            String folderId,
            String name,
            String path,
            boolean present,
            long createdAt) {

        static ProjectFolderResponse from(ProjectFolderStatus status) {
            AdvisorProjectFolder folder = status.folder();
            return new ProjectFolderResponse(
                    folder.folderId(),
                    folder.name(),
                    folder.path(),
                    status.present(),
                    folder.createdAt().toEpochMilli());
        }
    }

    @GetMapping
    public List<ProjectFolderResponse> list() {
        return manager.list().stream()
                .map(ProjectFolderResponse::from)
                .toList();
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<ProjectFolderResponse> get(@PathVariable("folderId") String folderId) {
        return manager.find(folderId)
                .map(status -> ResponseEntity.ok(ProjectFolderResponse.from(status)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ProjectFolderResponse create(@RequestBody ProjectFolderRequest request) {
        ProjectFolderStatus created = manager.create(toDraft(request));
        LOG.debug("Created advisor project folder: folder_id={} name={} path={}",
                created.folder().folderId(), created.folder().name(), created.folder().path());
        return ProjectFolderResponse.from(created);
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<ProjectFolderResponse> update(
            @PathVariable("folderId") String folderId, @RequestBody ProjectFolderRequest request) {

        return manager.update(folderId, toDraft(request))
                .map(status -> ResponseEntity.ok(ProjectFolderResponse.from(status)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> delete(@PathVariable("folderId") String folderId) {
        if (manager.delete(folderId)) {
            LOG.debug("Deleted advisor project folder: folder_id={}", folderId);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private static ProjectFolderDraft toDraft(ProjectFolderRequest request) {
        return new ProjectFolderDraft(request.name(), request.path());
    }
}
