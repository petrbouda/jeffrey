/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.ExternalProjectLink;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.recording.ProjectRecordingInitializer;

import java.util.function.Function;

public interface ProjectManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    void initialize();

    ProfilesManager profilesManager();

    RecordingsManager recordingsManager();

    RepositoryManager repositoryManager();

    SchedulerManager schedulerManager();

    SettingsManager settingsManager();

    ProjectRecordingInitializer recordingInitializer();

    ExternalProjectLink createProjectExternalLink(ExternalProjectLink externalProjectLink);

    ProjectInfo info();

    void delete();
}
