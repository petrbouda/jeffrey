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

package pbouda.jeffrey.local.persistence.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.local.persistence.model.QuickGroupInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcQuickGroupRepository implements QuickGroupRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO quick_groups (group_id, group_name, created_at)
                VALUES (:group_id, :group_name, :created_at)""";

    //language=SQL
    private static final String FIND_BY_ID =
            "SELECT * FROM quick_groups WHERE group_id = :group_id";

    //language=SQL
    private static final String FIND_ALL =
            "SELECT * FROM quick_groups ORDER BY group_name";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM quick_groups WHERE group_id = :group_id";

    private static final RowMapper<QuickGroupInfo> ROW_MAPPER = (rs, _) -> new QuickGroupInfo(
            rs.getString("group_id"),
            rs.getString("group_name"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant()
    );

    private final DatabaseClient databaseClient;

    public JdbcQuickGroupRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.QUICK_GROUPS);
    }

    @Override
    public void insert(QuickGroupInfo group) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", group.groupId())
                .addValue("group_name", group.groupName())
                .addValue("created_at", group.createdAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_QUICK_GROUP, INSERT, params);
    }

    @Override
    public Optional<QuickGroupInfo> findById(String groupId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        return databaseClient.querySingle(StatementLabel.FIND_QUICK_GROUP, FIND_BY_ID, params, ROW_MAPPER);
    }

    @Override
    public List<QuickGroupInfo> findAll() {
        return databaseClient.query(StatementLabel.FIND_ALL_QUICK_GROUPS, FIND_ALL, ROW_MAPPER);
    }

    @Override
    public void delete(String groupId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        databaseClient.delete(StatementLabel.DELETE_QUICK_GROUP, DELETE, params);
    }
}
