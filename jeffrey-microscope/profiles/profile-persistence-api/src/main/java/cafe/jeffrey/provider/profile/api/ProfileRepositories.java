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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;

/**
 * Profile-specific repository factories used by the profile-management domain.
 * Contains methods for accessing repositories related to profile data, events, and caching.
 */
public interface ProfileRepositories {

    /**
     * Creates a database client provider for the given profile database connection.
     *
     * @param dataSource the profile database connection
     * @return a new database client provider for the profile
     */
    DatabaseClientProvider databaseClientProvider(DataSource dataSource);

    /**
     * Creates an event repository for accessing profile event data.
     *
     * @param dataSource the profile database connection
     * @return a new event repository for the profile
     */
    ProfileEventRepository newEventRepository(DataSource dataSource);

    /**
     * Creates an event stream repository for streaming profile event data.
     *
     * @param dataSource the profile database connection
     * @return a new event stream repository for the profile
     */
    ProfileEventStreamRepository newEventStreamRepository(DataSource dataSource);

    /**
     * Creates an event type repository for accessing profile event types.
     *
     * @param dataSource the profile database connection
     * @return a new event type repository for the profile
     */
    ProfileEventTypeRepository newEventTypeRepository(DataSource dataSource);

    /**
     * Creates a cache repository for accessing profile-specific cached data.
     *
     * @param dataSource the profile database connection
     * @return a new cache repository for the profile
     */
    ProfileCacheRepository newProfileCacheRepository(DataSource dataSource);

    /**
     * Creates a profile info repository for accessing profile context information
     * (workspace_id, project_id).
     *
     * @param dataSource the profile database connection
     * @return a new profile info repository for the profile
     */
    ProfileInfoRepository newProfileInfoRepository(DataSource dataSource);

    /**
     * Creates a frame repository for accessing and manipulating profile frame data.
     *
     * @param dataSource the profile database connection
     * @return a new frame repository for the profile
     */
    ProfileFrameRepository newFrameRepository(DataSource dataSource);

    /**
     * Creates a tools repository for profile data transformation operations
     * (collapse frames, remove frames, trim time range).
     *
     * @param dataSource the profile database connection
     * @return a new tools repository for the profile
     */
    ProfileToolsRepository newToolsRepository(DataSource dataSource);

    /**
     * Creates a span repository for reading async-profiler {@code profiler.Span} events.
     *
     * @param dataSource the profile database connection
     * @return a new span repository for the profile
     */
    SpanRepository newSpanRepository(DataSource dataSource);

    /**
     * Creates a trace repository for the traces derived from the profile's events.
     *
     * @param dataSource the profile database connection
     * @return a new trace repository for the profile
     */
    TraceRepository newTraceRepository(DataSource dataSource);

    /**
     * Creates a trace attribute repository over the attribute index derived from the profile's spans.
     *
     * @param dataSource the profile database connection
     * @return a new trace attribute repository for the profile
     */
    TraceAttributeRepository newTraceAttributeRepository(DataSource dataSource);

    /**
     * Creates the repository that settles what a method trace contributes when durations are summed.
     *
     * @param dataSource the profile database connection
     * @return a new method-trace weight repository for the profile
     */
    MethodTraceWeightRepository newMethodTraceWeightRepository(DataSource dataSource);

    /**
     * Creates a repository for the terminal snapshots of staged background runs (heap-dump
     * initialization, or any future pipeline).
     *
     * @param dataSource the profile database connection
     * @return a new pipeline-run repository for the profile
     */
    PipelineRunRepository newPipelineRunRepository(DataSource dataSource);
}
