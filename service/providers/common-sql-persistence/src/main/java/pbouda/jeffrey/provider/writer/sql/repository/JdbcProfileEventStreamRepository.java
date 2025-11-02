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

package pbouda.jeffrey.provider.writer.sql.repository;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.query.JdbcEventStreamerFactory;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactory;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolver;

import java.util.List;

import static pbouda.jeffrey.provider.writer.sql.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventStreamRepository implements ProfileEventStreamRepository {

    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final String profileId;
    private final DatabaseClient databaseClient;

    public JdbcProfileEventStreamRepository(
            QueryBuilderFactoryResolver queryBuilderFactoryResolver,
            String profileId,
            DatabaseClientProvider databaseClientProvider) {

        this.queryBuilderFactoryResolver = queryBuilderFactoryResolver;
        this.profileId = profileId;
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
    }

    @Override
    public EventStreamerFactory newEventStreamerFactory(EventQueryConfigurer configurer) {
        List<Type> types = configurer.eventTypes();
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, types);
        return new JdbcEventStreamerFactory(databaseClient, configurer, factory);
    }
}
