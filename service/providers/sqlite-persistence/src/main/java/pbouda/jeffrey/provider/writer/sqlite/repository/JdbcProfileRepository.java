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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.provider.api.repository.ProfileRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcProfileRepository implements ProfileRepository {

    //language=SQL
    private static final String ENABLE_PROFILE = """
            UPDATE profiles SET enabled_at = :enabled_at WHERE profile_id = :profile_id
            """;

    //language=SQL
    private static final String SELECT_SINGLE_PROFILE = """
            SELECT * FROM profiles WHERE profile_id = :profile_id
            """;

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
            DELETE FROM event_fields WHERE profile_id = '%profile_id%';
            DELETE FROM profiles WHERE profile_id = '%profile_id%';
            COMMIT;
            """;

    private final String profileId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProfileRepository(String profileId, JdbcTemplate jdbcTemplate) {
        this.profileId = profileId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public Optional<ProfileInfo> find() {
        Map<String, String> params = Map.of("profile_id", profileId);
        List<ProfileInfo> results = jdbcTemplate.query(SELECT_SINGLE_PROFILE, params, Mappers.profileInfoMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void enableProfile() {
        Map<String, Object> params = Map.of(
                "profile_id", profileId,
                "enabled_at", Instant.now().toEpochMilli());

        jdbcTemplate.update(ENABLE_PROFILE, params);
    }

    @Override
    public void delete() {
        jdbcTemplate.getJdbcTemplate()
                .update(DELETE_PROFILE.replaceAll("%profile_id%", profileId));

        jdbcTemplate.getJdbcTemplate().execute("PRAGMA wal_checkpoint(TRUNCATE);");
    }
}
