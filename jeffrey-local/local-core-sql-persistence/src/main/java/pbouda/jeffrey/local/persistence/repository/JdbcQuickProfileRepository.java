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
import pbouda.jeffrey.local.persistence.model.QuickProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcQuickProfileRepository implements QuickProfileRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO quick_profiles (profile_id, profile_name, group_name, event_source, created_at, profiling_started_at, profiling_finished_at)
                VALUES (:profile_id, :profile_name, :group_name, :event_source, :created_at, :profiling_started_at, :profiling_finished_at)""";

    //language=SQL
    private static final String FIND_BY_ID =
            "SELECT * FROM quick_profiles WHERE profile_id = :profile_id";

    //language=SQL
    private static final String FIND_ALL =
            "SELECT * FROM quick_profiles ORDER BY created_at DESC";

    //language=SQL
    private static final String FIND_DISTINCT_GROUPS =
            "SELECT DISTINCT group_name FROM quick_profiles WHERE group_name IS NOT NULL ORDER BY group_name";

    //language=SQL
    private static final String UPDATE_GROUP =
            "UPDATE quick_profiles SET group_name = :group_name WHERE profile_id = :profile_id";

    //language=SQL
    private static final String UPDATE_PROFILE_NAME =
            "UPDATE quick_profiles SET profile_name = :profile_name WHERE profile_id = :profile_id";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM quick_profiles WHERE profile_id = :profile_id";

    private static final RowMapper<QuickProfileInfo> ROW_MAPPER = (rs, _) -> new QuickProfileInfo(
            rs.getString("profile_id"),
            rs.getString("profile_name"),
            rs.getString("group_name"),
            RecordingEventSource.valueOf(rs.getString("event_source")),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("profiling_started_at", OffsetDateTime.class).toInstant(),
            rs.getObject("profiling_finished_at", OffsetDateTime.class).toInstant()
    );

    private static final RowMapper<String> GROUP_NAME_MAPPER = (rs, _) -> rs.getString("group_name");

    private final DatabaseClient databaseClient;

    public JdbcQuickProfileRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.QUICK_PROFILES);
    }

    @Override
    public void insert(QuickProfileInfo profile) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profile.profileId())
                .addValue("profile_name", profile.profileName())
                .addValue("group_name", profile.groupName())
                .addValue("event_source", profile.eventSource().name())
                .addValue("created_at", profile.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("profiling_started_at", profile.profilingStartedAt().atOffset(ZoneOffset.UTC))
                .addValue("profiling_finished_at", profile.profilingFinishedAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_QUICK_PROFILE, INSERT, params);
    }

    @Override
    public Optional<QuickProfileInfo> find(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        return databaseClient.querySingle(StatementLabel.FIND_QUICK_PROFILE, FIND_BY_ID, params, ROW_MAPPER);
    }

    @Override
    public List<QuickProfileInfo> findAll() {
        return databaseClient.query(StatementLabel.FIND_ALL_QUICK_PROFILES, FIND_ALL, ROW_MAPPER);
    }

    @Override
    public List<String> findDistinctGroups() {
        return databaseClient.query(StatementLabel.FIND_DISTINCT_QUICK_PROFILE_GROUPS, FIND_DISTINCT_GROUPS, GROUP_NAME_MAPPER);
    }

    @Override
    public void updateGroupName(String profileId, String groupName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("group_name", groupName);

        databaseClient.update(StatementLabel.UPDATE_QUICK_PROFILE_GROUP, UPDATE_GROUP, params);
    }

    @Override
    public void updateProfileName(String profileId, String profileName) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("profile_name", profileName);

        databaseClient.update(StatementLabel.UPDATE_QUICK_PROFILE_NAME, UPDATE_PROFILE_NAME, params);
    }

    @Override
    public void delete(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        databaseClient.delete(StatementLabel.DELETE_QUICK_PROFILE, DELETE, params);
    }
}
