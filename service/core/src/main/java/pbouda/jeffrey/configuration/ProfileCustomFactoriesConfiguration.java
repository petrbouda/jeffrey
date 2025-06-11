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

package pbouda.jeffrey.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.manager.custom.JdbcPoolManager;
import pbouda.jeffrey.manager.custom.JdbcPoolManagerImpl;
import pbouda.jeffrey.provider.api.repository.Repositories;

@Configuration
public class ProfileCustomFactoriesConfiguration {

    @Bean
    public JdbcPoolManager.Factory jdbcPoolManagerFactory(Repositories repositories) {
        return profileInfo -> new JdbcPoolManagerImpl(
                profileInfo, repositories.newEventRepository(profileInfo.id()));
    }
}
