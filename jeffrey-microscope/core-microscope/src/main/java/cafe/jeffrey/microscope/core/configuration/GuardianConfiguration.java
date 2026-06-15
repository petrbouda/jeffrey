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
import cafe.jeffrey.microscope.core.guardian.DbGuardDefinitions;
import cafe.jeffrey.microscope.persistence.api.GuardianGuardRepository;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcGuardianGuardRepository;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;

/**
 * Wires the database-backed Guardian guard configuration: the CRUD repository over the central
 * {@code guardian_guards} table and the {@link GuardDefinitions} provider that maps rows into the
 * Guardian engine's domain model.
 */
@Configuration
public class GuardianConfiguration {

    @Bean
    public GuardianGuardRepository guardianGuardRepository(MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new JdbcGuardianGuardRepository(localCorePersistenceProvider.databaseClientProvider());
    }

    @Bean
    public GuardDefinitions guardDefinitions(GuardianGuardRepository guardianGuardRepository) {
        return new DbGuardDefinitions(guardianGuardRepository);
    }
}
