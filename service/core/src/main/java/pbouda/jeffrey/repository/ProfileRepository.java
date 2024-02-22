package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class ProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_PROFILE = """
            INSERT INTO main.profiles (
                id,
                name,
                created_at,
                started_at,
                recording_path
            ) VALUES (?, ?, ?, ?, ?)
            """;

    public ProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertProfile(ProfileInfo profile) {
        jdbcTemplate.update(
                INSERT_PROFILE,
                profile.id(),
                profile.name(),
                profile.createdAt().toEpochMilli(),
                profile.startedAt().toEpochMilli(),
                profile.recordingPath());
    }

    public ProfileInfo getProfile(String profileId) {
        return jdbcTemplate.queryForObject(
                "SELECT * FROM main.profiles WHERE id = ?", ProfileRepository::profileInfoMapper, profileId);
    }

    public List<ProfileInfo> getProfilesByRecordingPath(Path recordingPath) {
        return jdbcTemplate.query(
                "SELECT * FROM main.profiles WHERE recording_path = ?", ProfileRepository::profileInfoMapper, recordingPath);
    }

    public List<ProfileInfo> all() {
        return jdbcTemplate.query("SELECT * FROM main.profiles", ProfileRepository::profileInfoMapper);
    }

    public void delete(String profileId) {
        jdbcTemplate.update("DELETE FROM main.profiles WHERE id = ?", profileId);
    }

    private static ProfileInfo profileInfoMapper(ResultSet rs, int ignored) {
        try {
            return new ProfileInfo(
                    rs.getString("id"),
                    rs.getString("name"),
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    Instant.ofEpochMilli(rs.getLong("started_at")),
                    Path.of(rs.getString("recording_path")));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot retrieve a profile info", e);
        }
    }
}
