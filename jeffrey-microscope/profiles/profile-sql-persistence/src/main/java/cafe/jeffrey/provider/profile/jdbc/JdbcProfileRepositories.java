/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;

public class JdbcProfileRepositories implements ProfileRepositories {

    private final SQLFormatter sqlFormatter;
    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final FrameResolutionMode frameResolutionMode;

    // Frames of the single currently-open profile; shared across repositories so that
    // frame-mutating operations can invalidate what the flamegraph streaming reads
    private final SingleSlotFramesCache framesCache = new SingleSlotFramesCache();

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
                queryBuilderFactoryResolver, profileClientProvider, frameResolutionMode,
                new FramesCacheSlot(framesCache, dataSource));
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        // Wrapped here rather than at each call site: the profile-wide summaries are asked for by
        // the Event Viewer, the feature checks, the flamegraph panels and both
        // exporters, and every one of them wants the same unchanging answer.
        return new CachingProfileEventTypeRepository(
                new JdbcProfileEventTypeRepository(sqlFormatter, profileClientProvider),
                newProfileCacheRepository(dataSource));
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

    @Override
    public ProfileFrameRepository newFrameRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileFrameRepository(profileClientProvider, new FramesCacheSlot(framesCache, dataSource));
    }

    @Override
    public ProfileToolsRepository newToolsRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcProfileToolsRepository(profileClientProvider, new FramesCacheSlot(framesCache, dataSource));
    }

    @Override
    public SpanRepository newSpanRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcSpanRepository(profileClientProvider);
    }

    @Override
    public TraceRepository newTraceRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcTraceRepository(profileClientProvider);
    }

    @Override
    public MethodTraceWeightRepository newMethodTraceWeightRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcMethodTraceWeightRepository(profileClientProvider);
    }

    @Override
    public TraceAttributeRepository newTraceAttributeRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcTraceAttributeRepository(profileClientProvider);
    }

    @Override
    public PipelineRunRepository newPipelineRunRepository(DataSource dataSource) {
        DatabaseClientProvider profileClientProvider = new DatabaseClientProvider(dataSource);
        return new JdbcPipelineRunRepository(profileClientProvider);
    }
}
