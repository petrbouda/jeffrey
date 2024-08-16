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

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.repository.model.SubSecondInfo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SubSecondRepository {

    private static final String INSERT = """
            INSERT INTO main.heatmaps (
                id,
                profile_id,
                name,
                created_at,
                content
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_INFO = """
            SELECT id, name, created_at
            FROM main.heatmaps WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT = """
            SELECT content
            FROM main.heatmaps WHERE id = ? AND profile_id = ?
            """;

    private static final String SELECT_CONTENT_BY_NAME = """
            SELECT content
            FROM main.heatmaps WHERE name = ? AND profile_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SubSecondRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(SubSecondInfo heatmap, byte[] content) {
        jdbcTemplate.update(
                INSERT,
                new Object[]{
                        heatmap.id(),
                        heatmap.profileId(),
                        heatmap.name(),
                        heatmap.createdAt().getEpochSecond(),
                        new SqlLobValue(content)
                },
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.BLOB});
    }

    public SubSecondInfo info(String profileId, String heatmapId) {
        return jdbcTemplate.queryForObject(SELECT_INFO, SubSecondRepository::infoMapper, heatmapId, profileId);
    }

    public Optional<byte[]> content(String profileId, String heatmapId) {
        try {
            byte[] content = jdbcTemplate.queryForObject(SELECT_CONTENT, Repos.contentByteArray(), heatmapId, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> contentByName(String profileId, String heatmapName) {
        try {
            byte[] content = jdbcTemplate.queryForObject(
                    SELECT_CONTENT_BY_NAME, Repos.contentByteArray(), heatmapName, profileId);
            return Optional.ofNullable(content);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<SubSecondInfo> all(String profileId) {
        return jdbcTemplate.query(
                "SELECT * FROM main.heatmaps WHERE profile_id = ?", SubSecondRepository::infoMapper, profileId);
    }

    public void delete(String profileId, String heatmapId) {
        jdbcTemplate.update("DELETE FROM main.heatmaps WHERE id = ? AND profile_id = ?", heatmapId, profileId);
    }

    public void deleteByProfileId(String profileId) {
        jdbcTemplate.update("DELETE FROM main.heatmaps WHERE profile_id = ?", profileId);
    }


    private static SubSecondInfo infoMapper(ResultSet rs, int ignored) {
        try {
            return new SubSecondInfo(
                    rs.getString("id"),
                    rs.getString("profile_id"),
                    rs.getString("name"),
                    Instant.ofEpochSecond(rs.getInt("created_at")));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot retrieve a heatmap info", e);
        }
    }
}
