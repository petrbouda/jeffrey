package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.JdbcTemplate;

public class ProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void delete(String profileId) {
        jdbcTemplate.update("DELETE FROM main.profiles WHERE id = ?", profileId);
    }
}
