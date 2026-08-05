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

package cafe.jeffrey.microscope.core.manager.advisor;

import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolderRepository;
import cafe.jeffrey.shared.common.IDGenerator;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * CRUD over the installation-wide list of working copies the Advisor may read. Assigns identifiers and
 * creation timestamps, keeps names unique, and pairs every folder it returns with what the filesystem
 * currently says about it.
 *
 * <p>Names are unique because a folder is picked by name when the Advisor runs: two entries called the
 * same thing would be indistinguishable at the moment of choosing.
 */
public class AdvisorProjectFoldersManager {

    private static final String NAME_TAKEN_PREFIX = "A project folder named '";
    private static final String NAME_TAKEN_SUFFIX = "' already exists";

    private final AdvisorProjectFolderRepository repository;
    private final FolderProbe folderProbe;
    private final Clock clock;

    public AdvisorProjectFoldersManager(
            AdvisorProjectFolderRepository repository, FolderProbe folderProbe, Clock clock) {

        this.repository = repository;
        this.folderProbe = folderProbe;
        this.clock = clock;
    }

    public List<ProjectFolderStatus> list() {
        return repository.findAll().stream()
                .map(this::withStatus)
                .toList();
    }

    public Optional<ProjectFolderStatus> find(String folderId) {
        return repository.find(folderId).map(this::withStatus);
    }

    public ProjectFolderStatus create(ProjectFolderDraft draft) {
        requireNameFree(draft.name(), null);

        AdvisorProjectFolder folder = new AdvisorProjectFolder(
                IDGenerator.generate(), draft.name(), draft.path(), clock.instant());

        repository.insert(folder);
        return withStatus(folder);
    }

    /** Updates an existing folder, preserving its original {@code createdAt}. */
    public Optional<ProjectFolderStatus> update(String folderId, ProjectFolderDraft draft) {
        return repository.find(folderId).map(existing -> {
            requireNameFree(draft.name(), folderId);

            AdvisorProjectFolder folder = new AdvisorProjectFolder(
                    folderId, draft.name(), draft.path(), existing.createdAt());

            repository.update(folder);
            return withStatus(folder);
        });
    }

    public boolean delete(String folderId) {
        if (repository.find(folderId).isEmpty()) {
            return false;
        }
        repository.delete(folderId);
        return true;
    }

    /**
     * @param keptFolderId the folder allowed to hold the name already — the one being edited, or null
     *                     when creating
     */
    private void requireNameFree(String name, String keptFolderId) {
        boolean taken = repository.findByName(name)
                .filter(existing -> !existing.folderId().equals(keptFolderId))
                .isPresent();

        if (taken) {
            throw new IllegalArgumentException(NAME_TAKEN_PREFIX + name + NAME_TAKEN_SUFFIX);
        }
    }

    private ProjectFolderStatus withStatus(AdvisorProjectFolder folder) {
        return new ProjectFolderStatus(folder, folderProbe.isDirectory(folder.path()));
    }
}
