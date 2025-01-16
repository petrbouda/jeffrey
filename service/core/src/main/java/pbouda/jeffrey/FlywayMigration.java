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

package pbouda.jeffrey;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.repository.factory.JdbcTemplateFactory;
import pbouda.jeffrey.repository.factory.JdbcTemplateProfileFactory;

public abstract class FlywayMigration {

    public enum MigrationTarget {
        PROFILE_COMMON("profile/common"),
        PROFILE_EVENTS("profile/events"),
        PROJECT("project"),
        GLOBAL("global");

        private final String type;

        MigrationTarget(String type) {
            this.type = type;
        }
    }

    public static void migrate(JdbcTemplate jdbcTemplate, MigrationTarget target) {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .locations("classpath:db/migration/" + target.type)
                .sqlMigrationPrefix("V")
                .sqlMigrationSeparator("__")
                .load();

        flyway.migrate();
    }

    public static void migrate(HomeDirs homeDirs) {
        migrate(JdbcTemplateFactory.create(homeDirs), MigrationTarget.GLOBAL);
    }

    public static void migrate(ProjectDirs projectDirs) {
        migrate(JdbcTemplateFactory.create(projectDirs), MigrationTarget.PROJECT);
    }

    public static void migrateCommon(ProfileDirs profileDirs) {
        migrate(JdbcTemplateProfileFactory.createCommon(profileDirs), MigrationTarget.PROFILE_COMMON);
    }

    public static void migrateEvents(ProfileDirs profileDirs) {
        migrate(JdbcTemplateProfileFactory.createEvents(profileDirs), MigrationTarget.PROFILE_EVENTS);
    }
}
