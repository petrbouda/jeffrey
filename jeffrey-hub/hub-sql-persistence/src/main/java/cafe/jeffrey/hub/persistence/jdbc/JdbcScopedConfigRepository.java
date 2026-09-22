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


package cafe.jeffrey.hub.persistence.jdbc;

import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.persistence.api.ScopedConfigRepository;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Timestamp;
import java.util.List;

public class JdbcScopedConfigRepository implements ScopedConfigRepository {

    /**
     * The UNIQUE key of a row, computed in SQL from the scope ids and the type so that the ids
     * themselves can stay NULL where the scope has none. Spelled once and spliced into every
     * statement that has to name a row.
     */
    private static final String ENTRY_KEY =
            "COALESCE(:workspace_id, '') || ':' || COALESCE(:project_id, '') || ':' || :config_type";

    /** The same key without the type, for statements that address a whole scope. */
    private static final String SCOPE_PREFIX = "COALESCE(:workspace_id, '') || ':' || COALESCE(:project_id, '') || ':'";

    //language=SQL
    private static final String UPSERT_CONFIG = """
            INSERT INTO scoped_configs (scope, workspace_id, project_id, config_type, entry_key, config_value, updated_at)
            VALUES (:scope, :workspace_id, :project_id, :config_type, <ENTRY_KEY>, :config_value, :updated_at)
            ON CONFLICT (entry_key) DO UPDATE SET config_value = EXCLUDED.config_value, updated_at = EXCLUDED.updated_at"""
            .replace("<ENTRY_KEY>", ENTRY_KEY);

    // A NULL parameter matches nothing by equality, so IS NOT DISTINCT FROM is what lets one
    // statement address a global, a workspace and a project scope alike.
    //language=SQL
    private static final String FIND_CONFIG = """
            SELECT * FROM scoped_configs
            WHERE workspace_id IS NOT DISTINCT FROM :workspace_id
              AND project_id IS NOT DISTINCT FROM :project_id""";

    // The global row, the workspace's own, and every project of that workspace.
    //language=SQL
    private static final String FIND_WORKSPACE_CONFIGS = """
            SELECT * FROM scoped_configs
            WHERE workspace_id = :workspace_id OR (workspace_id IS NULL AND project_id IS NULL)""";

    //language=SQL
    private static final String DELETE_CONFIG = """
            DELETE FROM scoped_configs WHERE entry_key = <ENTRY_KEY>"""
            .replace("<ENTRY_KEY>", ENTRY_KEY);

    //language=SQL
    private static final String DELETE_CONFIGS = """
            DELETE FROM scoped_configs WHERE entry_key LIKE <SCOPE_PREFIX> || '%'"""
            .replace("<SCOPE_PREFIX>", SCOPE_PREFIX);

    private final DatabaseClient databaseClient;

    public JdbcScopedConfigRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.SCOPED_CONFIG);
    }

    @Override
    public void upsert(ScopedConfigEntry entry) {
        SqlParameterSource paramSource = scope(entry.key())
                .addValue("config_type", entry.type().name())
                .addValue("config_value", entry.value())
                .addValue("updated_at", Timestamp.from(entry.updatedAt()));

        databaseClient.insert(StatementLabel.UPSERT_SCOPED_CONFIG, UPSERT_CONFIG, paramSource);
    }

    @Override
    public List<ScopedConfigEntry> find(ScopedConfigKey key) {
        return databaseClient.query(
                StatementLabel.FIND_SCOPED_CONFIG, FIND_CONFIG, scope(key), entryMapper());
    }

    @Override
    public List<ScopedConfigEntry> findForWorkspace(String workspaceId) {
        SqlParameterSource paramSource = new MapSqlParameterSource().addValue("workspace_id", workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_WORKSPACE_SCOPED_CONFIGS, FIND_WORKSPACE_CONFIGS, paramSource, entryMapper());
    }

    @Override
    public void delete(ScopedConfigKey key, ConfigType type) {
        SqlParameterSource paramSource = scope(key).addValue("config_type", type.name());

        databaseClient.delete(StatementLabel.DELETE_SCOPED_CONFIG, DELETE_CONFIG, paramSource);
    }

    @Override
    public void deleteAll(ScopedConfigKey key) {
        databaseClient.delete(StatementLabel.DELETE_SCOPED_CONFIGS, DELETE_CONFIGS, scope(key));
    }

    private static MapSqlParameterSource scope(ScopedConfigKey key) {
        return new MapSqlParameterSource()
                .addValue("scope", key.scope().name())
                .addValue("workspace_id", key.workspaceId())
                .addValue("project_id", key.projectId());
    }

    private static RowMapper<ScopedConfigEntry> entryMapper() {
        return (rs, _) -> new ScopedConfigEntry(
                new ScopedConfigKey(
                        ConfigScope.valueOf(rs.getString("scope")),
                        rs.getString("workspace_id"),
                        rs.getString("project_id")),
                ConfigType.valueOf(rs.getString("config_type")),
                rs.getString("config_value"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
