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

package pbouda.jeffrey.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.repository.model.ProjectInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class ProjectsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ProjectInfo projectInfo) {
        try {
            jdbcTemplate.update("INSERT INTO projects (id, name, created_at) VALUES (?, ?, ?)",
                    projectInfo.id(), projectInfo.name(), projectInfo.createdAt().getEpochSecond());
        } catch (DataAccessException ex) {
            if (ex.getMessage().contains("A UNIQUE constraint failed")) {
                throw new RuntimeException("Project already exists", ex);
            }
            throw new RuntimeException("Failed to create the project", ex);
        }
    }

    public List<ProjectInfo> findAllProjectInfos() {
        return jdbcTemplate.query("SELECT * FROM projects", projectInfoRowMapper());
    }

    public Optional<ProjectInfo> findProjectInfoById(String projectId) {
        List<ProjectInfo> query = jdbcTemplate.query(
                "SELECT * FROM projects WHERE id = ?", projectInfoRowMapper(), projectId);
        return query.isEmpty() ? Optional.empty() : Optional.of(query.getFirst());
    }

    public void delete(String projectId) {
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", projectId);
    }

    private static RowMapper<ProjectInfo> projectInfoRowMapper() {
        return (rs, rowNum) -> new ProjectInfo(
                rs.getString("id"),
                rs.getString("name"),
                Instant.ofEpochSecond(rs.getLong("created_at"))
        );
    }
}
