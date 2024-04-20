package pbouda.jeffrey.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;

public class JdbcTemplateFactory {

    private final WorkingDirs workingDirs;

    public JdbcTemplateFactory(WorkingDirs workingDirs) {
        this.workingDirs = workingDirs;
    }

    public JdbcTemplate create(ProfileInfo profileInfo) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + workingDirs.profileDbFile(profileInfo));
        return new JdbcTemplate(dataSource);
    }
}
