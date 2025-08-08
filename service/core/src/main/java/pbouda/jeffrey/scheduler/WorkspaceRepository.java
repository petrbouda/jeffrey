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

package pbouda.jeffrey.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.scheduler.model.ProjectAttribute;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class WorkspaceRepository {

    private static final TypeReference<List<ProjectAttribute>> LIST_ATTRIBUTE_TYPE =
            new TypeReference<List<ProjectAttribute>>() {
            };

    //language=sql
    private static final String SELECT_PROJECTS = """
            SELECT * FROM workspace_projects
            """;

    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(10);
    private static final String DATABASE_FILENAME = "workspace.db";

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(Path workspacePath) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }
        Path workspaceDb = workspacePath.resolve(DATABASE_FILENAME);
        this.jdbcTemplate = new JdbcTemplate(notPooled(workspaceDb));
    }

    public List<WorkspaceProject> allProjects() {
        return jdbcTemplate.query(SELECT_PROJECTS, (rs, _) -> {
            return new WorkspaceProject(
                    rs.getString("project_id"),
                    rs.getString("project_name"),
                    rs.getString("workspace_id"),
                    rs.getLong("created_at"),
                    toAttributes(rs.getString("attributes"))
            );
        });
    }

    private static List<ProjectAttribute> toAttributes(String content) {
        try {
            return Json.mapper().readValue(content, LIST_ATTRIBUTE_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse a content to a list of attributes", e);
        }
    }

    private static DataSource notPooled(Path dbPath) {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
        config.setBusyTimeout((int) DEFAULT_BUSY_TIMEOUT.toMillis());

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(buildUrl(dbPath));
        return dataSource;
    }

    private static String buildUrl(Path dbPath) {
        return "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }
}
