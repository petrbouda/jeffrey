/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile;

import pbouda.jeffrey.provider.profile.query.SQLFormatter;
import pbouda.jeffrey.provider.profile.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.profile.repository.*;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;

public class JdbcProfileRepositories implements ProfileRepositories {

    private final SQLFormatter sqlFormatter;
    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final FrameResolutionMode frameResolutionMode;

    public JdbcProfileRepositories(
            SQLFormatter sqlFormatter,
            QueryBuilderFactoryResolver queryBuilderFactoryResolver,
            FrameResolutionMode frameResolutionMode) {

        this.sqlFormatter = sqlFormatter;
        this.queryBuilderFactoryResolver = queryBuilderFactoryResolver;
        this.frameResolutionMode = frameResolutionMode;
    }

    @Override
    public DatabaseClientProvider databaseClientProvider(DataSource dataSource) {
        return new DatabaseClientProvider(dataSource);
    }

    @Override
    public ProfileEventRepository newEventRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileEventRepository(sqlFormatter, profileClientProvider);
    }

    @Override
    public ProfileEventStreamRepository newEventStreamRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileEventStreamRepository(
                queryBuilderFactoryResolver, profileClientProvider, frameResolutionMode);
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileEventTypeRepository(sqlFormatter, profileClientProvider);
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileCacheRepository(profileClientProvider);
    }

    @Override
    public ProfileInfoRepository newProfileInfoRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileInfoRepository(profileClientProvider);
    }
}
