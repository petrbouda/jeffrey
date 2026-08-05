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

package cafe.jeffrey.microscope.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.microscope.core.manager.advisor.AdvisorProjectFoldersManager;
import cafe.jeffrey.microscope.core.manager.advisor.FilesystemFolderProbe;
import cafe.jeffrey.microscope.core.manager.advisor.FolderProbe;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolderRepository;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcAdvisorProjectFolderRepository;

import java.time.Clock;

/**
 * Wires the installation-wide Advisor configuration: the CRUD repository over the central
 * {@code advisor_project_folders} table and the manager that keeps its names unique and reports
 * whether each stored path still resolves.
 */
@Configuration
public class AdvisorConfiguration {

    @Bean
    public AdvisorProjectFolderRepository advisorProjectFolderRepository(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider) {

        return new JdbcAdvisorProjectFolderRepository(localCorePersistenceProvider.databaseClientProvider());
    }

    @Bean
    public FolderProbe folderProbe() {
        return new FilesystemFolderProbe();
    }

    @Bean
    public AdvisorProjectFoldersManager advisorProjectFoldersManager(
            AdvisorProjectFolderRepository advisorProjectFolderRepository, FolderProbe folderProbe, Clock clock) {

        return new AdvisorProjectFoldersManager(advisorProjectFolderRepository, folderProbe, clock);
    }
}
