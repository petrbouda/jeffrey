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

package pbouda.jeffrey.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import pbouda.jeffrey.FlywayMigration;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;

public class DatabaseMigrationInitializer implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        HomeDirs homeDirs = context.getBean(HomeDirs.class);
        homeDirs.initialize();

        for (ProjectInfo project : homeDirs.allProjects()) {
            // Migrate the database belonging to a single project
            ProjectDirs projectDirs = homeDirs.project(project);
            FlywayMigration.migrate(projectDirs);

            // Migration of all profiles belonging to the given project
            for (ProfileInfo profile : projectDirs.allProfiles()) {
                FlywayMigration.migrate(projectDirs.profile(profile));
            }
        }

        // Migrate the database belonging to Jeffrey
        FlywayMigration.migrate(homeDirs);
    }
}
