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

package pbouda.jeffrey.provider.writer.sqlite;

import pbouda.jeffrey.provider.api.repository.*;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.repository.*;

public class JdbcRepositories implements Repositories {

    private final DatabaseClient databaseClient;

    public JdbcRepositories(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public ProfileEventRepository newEventRepository(String profileId) {
        return new JdbcProfileEventRepository(profileId, databaseClient);
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(String profileId) {
        return new JdbcProfileEventTypeRepository(profileId, databaseClient);
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, databaseClient);
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository(String profileId) {
        return new JdbcProfileCacheRepository(profileId, databaseClient);
    }

    @Override
    public ProfileGraphRepository newProfileGraphRepository(String profileId) {
        return new JdbcProfileGraphRepository(profileId, databaseClient);
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(projectId, databaseClient);
    }

    @Override
    public ProjectRecordingRepository newProjectRecordingRepository(String projectId) {
        return new JdbcProjectRecordingRepository(projectId, databaseClient);
    }

    @Override
    public ProjectsRepository newProjectsRepository() {
        return new JdbcProjectsRepository(databaseClient);
    }

    @Override
    public SchedulerRepository newProjectSchedulerRepository(String projectId) {
        return new JdbcProjectSchedulerRepository(projectId, databaseClient);
    }

    @Override
    public SchedulerRepository newGlobalSchedulerRepository() {
        return new JdbcGlobalSchedulerRepository(databaseClient);
    }

    @Override
    public ProjectRepositoryRepository newProjectRepositoryRepository(String projectId) {
        return new JdbcProjectRepositoryRepository(projectId, databaseClient);
    }
}
