/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.project.repository;

import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectSessionManager;
import pbouda.jeffrey.project.repository.detection.AsprofStatusStrategy;
import pbouda.jeffrey.project.repository.detection.StatusStrategy;
import pbouda.jeffrey.project.repository.file.FileInfoProcessor;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.time.Clock;
import java.time.Duration;

public class AsprofWithTempFileRemoteRepositoryStorage extends AsprofFileRemoteRepositoryStorage {

    private final Duration finishedPeriod;
    private final Clock clock;

    public AsprofWithTempFileRemoteRepositoryStorage(
            ProjectInfo projectInfo,
            ProjectSessionManager sessionManager,
            HomeDirs homeDirs,
            ProjectRepositoryRepository projectRepositoryRepository,
            FileInfoProcessor fileInfoProcessor,
            Duration finishedPeriod,
            Clock clock) {
        super(projectInfo,
                sessionManager,
                homeDirs,
                projectRepositoryRepository,
                fileInfoProcessor,
                finishedPeriod,
                clock);

        this.finishedPeriod = finishedPeriod;
        this.clock = clock;
    }

    @Override
    protected StatusStrategy createStatusStrategy(DBRepositoryInfo repositoryInfo) {
        return new AsprofStatusStrategy(finishedPeriod, clock);
    }
}
