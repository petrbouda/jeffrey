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

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.provider.api.repository.*;
import pbouda.jeffrey.provider.writer.sqlite.repository.*;

import javax.sql.DataSource;

public class JdbcRepositories implements Repositories {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRepositories(DataSource datasource) {
        this.jdbcTemplate = new JdbcTemplate(datasource);
    }

    @Override
    public ProfileEventRepository newEventRepository(String profileId) {
        return new JdbcProfileEventRepository(profileId, jdbcTemplate);
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(String profileId) {
        return new JdbcProfileEventTypeRepository(profileId, jdbcTemplate);
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, jdbcTemplate);
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository(String profileId) {
        return new JdbcProfileCacheRepository(profileId, jdbcTemplate);
    }

    @Override
    public ProfileGraphRepository newProfileGraphRepository(String profileId) {
        return new JdbcProfileGraphRepository(profileId, jdbcTemplate);
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(projectId, jdbcTemplate);
    }

    @Override
    public ProjectRecordingRepository newProjectRecordingRepository(String projectId) {
        return new JdbcProjectRecordingRepository(projectId, jdbcTemplate);
    }

    @Override
    public ProjectsRepository newProjectsRepository() {
        return new JdbcProjectsRepository(jdbcTemplate);
    }

    @Override
    public ProjectSchedulerRepository newProjectSchedulerRepository(String projectId) {
        return new JdbcProjectSchedulerRepository(projectId, jdbcTemplate);
    }

    @Override
    public ProjectRepositoryRepository newProjectRepositoryRepository(String projectId) {
        return new JdbcProjectRepositoryRepository(projectId, jdbcTemplate);
    }
}
