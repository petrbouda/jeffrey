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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRow;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.util.Optional;

public class JdbcAdvisorSettingsRepository implements AdvisorSettingsRepository {

    private static final String PARAM_PROFILE_ID = "profile_id";

    //language=SQL
    private static final String FIND = """
            SELECT profile_id, source_path, modified_at
            FROM profile_advisor_settings
            WHERE profile_id = :profile_id""";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO profile_advisor_settings (profile_id, source_path, modified_at)
            VALUES (:profile_id, :source_path, :modified_at)
            ON CONFLICT (profile_id) DO UPDATE SET
                source_path = EXCLUDED.source_path,
                modified_at = EXCLUDED.modified_at""";

    private final DatabaseClient databaseClient;

    public JdbcAdvisorSettingsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.ADVISOR);
    }

    @Override
    public Optional<AdvisorSettingsRow> find(String profileId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, profileId);

        return databaseClient.querySingle(StatementLabel.FIND_ADVISOR_SETTINGS, FIND, params, mapper());
    }

    @Override
    public void upsert(AdvisorSettingsRow settings) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PROFILE_ID, settings.profileId())
                .addValue("source_path", settings.sourcePath())
                .addValue("modified_at", Timestamp.from(settings.modifiedAt()));

        databaseClient.insert(StatementLabel.UPSERT_ADVISOR_SETTINGS, UPSERT, params);
    }

    private static RowMapper<AdvisorSettingsRow> mapper() {
        return (rs, _) -> new AdvisorSettingsRow(
                rs.getString(PARAM_PROFILE_ID),
                rs.getString("source_path"),
                rs.getTimestamp("modified_at").toInstant());
    }
}
