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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.provider.api.repository.ProfileRepository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcProfileRepository implements ProfileRepository {

    //language=SQL
    private static final String ENABLE_PROFILE = """
            UPDATE profiles
                SET enabled_at = :enabled_at
                WHERE profile_id = :profile_id
            """;

    //language=SQL
    private static final String SELECT_SINGLE_PROFILE = """
            SELECT * FROM profiles WHERE profile_id = :profile_id
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
        List<ProfileInfo> results = jdbcTemplate.query(SELECT_SINGLE_PROFILE, params, profileInfoMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public void enableProfile() {
        Map<String, Object> params = Map.of(
                "profile_id", profileId,
                "enabled_at", Instant.now());

        jdbcTemplate.update(ENABLE_PROFILE, params);
    }

    private static RowMapper<ProfileInfo> profileInfoMapper() {
        return (rs, rowNum) -> {
            return new ProfileInfo(
                    rs.getString("profile_id"),
                    rs.getString("project_id"),
                    rs.getString("profile_name"),
                    rs.getObject("profiling_started_at", Instant.class),
                    rs.getObject("profiling_finished_at", Instant.class),
                    rs.getObject("created_at", Instant.class),
                    rs.getObject("enabled_at", Instant.class) != null);
        };
    }
}
