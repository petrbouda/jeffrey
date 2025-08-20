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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.provider.api.repository.ProfileRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class JdbcProfileRepository implements ProfileRepository {

    //language=SQL
    private static final String ENABLE_PROFILE =
            "UPDATE profiles SET enabled_at = :enabled_at WHERE profile_id = :profile_id";

    //language=SQL
    private static final String SELECT_SINGLE_PROFILE =
            "SELECT * FROM profiles WHERE profile_id = :profile_id";

    //language=SQL
    private static final String UPDATE_PROFILE_NAME =
            "UPDATE profiles SET profile_name = :profile_name WHERE profile_id = :profile_id";

    //language=SQL
    private static final String DELETE_PROFILE = """
            BEGIN TRANSACTION;
            DELETE FROM cache WHERE profile_id = '%profile_id%';
            DELETE FROM saved_graphs WHERE profile_id = '%profile_id%';
            DELETE FROM stacktrace_tags WHERE profile_id = '%profile_id%';
            DELETE FROM stacktraces WHERE profile_id = '%profile_id%';
            DELETE FROM threads WHERE profile_id = '%profile_id%';
            DELETE FROM events WHERE profile_id = '%profile_id%';
            DELETE FROM event_types WHERE profile_id = '%profile_id%';
            DELETE FROM profiles WHERE profile_id = '%profile_id%';
            COMMIT;""";

    private final String profileId;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcProfileRepository(String profileId, DataSource dataSource, Clock clock) {
        this.profileId = profileId;
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROFILES);
        this.clock = clock;
    }

    @Override
    public Optional<ProfileInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROFILE, SELECT_SINGLE_PROFILE, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public void enableProfile() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("enabled_at", clock.instant().toEpochMilli());

        databaseClient.update(StatementLabel.ENABLED_PROFILE, ENABLE_PROFILE, paramSource);
    }

    @Override
    public ProfileInfo update(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("profile_name", name);

        databaseClient.update(StatementLabel.UPDATE_PROFILE_NAME, UPDATE_PROFILE_NAME, paramSource);
        
        // Return the updated profile info
        return find().orElseThrow(() -> new RuntimeException("Profile not found after update"));
    }

    @Override
    public void delete() {
        databaseClient.delete(StatementLabel.DELETE_PROFILE, DELETE_PROFILE.replaceAll("%profile_id%", profileId));
        databaseClient.execute(StatementLabel.WAL_CHECK_POINT, "PRAGMA wal_checkpoint(TRUNCATE);");
    }
}
