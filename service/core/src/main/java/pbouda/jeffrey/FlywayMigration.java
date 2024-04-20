package pbouda.jeffrey;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.repository.JdbcTemplateFactory;
import pbouda.jeffrey.repository.model.ProfileInfo;

public abstract class FlywayMigration {

    public static void migrate(JdbcTemplate jdbcTemplate) {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration")
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    public static void migrate(WorkingDirs workingDirs, ProfileInfo profileInfo) {
        JdbcTemplateFactory jdbcTemplateFactory = new JdbcTemplateFactory(workingDirs);
        migrate(jdbcTemplateFactory.create(profileInfo));
    }
}
