/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.profile.persistence.api;

import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * One profile's database and the repositories over it. Every repository it hands out reads the same
 * {@link DataSource}, so a feature built from it never mixes two profiles' data, and the frame edits
 * one repository makes are seen by the others through the repositories' shared frames cache.
 *
 * @param dataSource   the profile's database
 * @param repositories the factory every repository here is built with
 */
public record ProfileDatabase(DataSource dataSource, ProfileRepositories repositories) {

    public ProfileDatabase {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(repositories, "repositories");
    }

    public DatabaseClientProvider clients() {
        return repositories.databaseClientProvider(dataSource);
    }

    public ProfileEventRepository events() {
        return repositories.newEventRepository(dataSource);
    }

    public ProfileEventStreamRepository eventStream() {
        return repositories.newEventStreamRepository(dataSource);
    }

    public ProfileEventTypeRepository eventTypes() {
        return repositories.newEventTypeRepository(dataSource);
    }

    public ProfileCacheRepository cache() {
        return repositories.newProfileCacheRepository(dataSource);
    }

    public ProfileFrameRepository frames() {
        return repositories.newFrameRepository(dataSource);
    }

    public ProfileToolsRepository tools() {
        return repositories.newToolsRepository(dataSource);
    }

    public SpanRepository spans() {
        return repositories.newSpanRepository(dataSource);
    }

    public TraceRepository traces() {
        return repositories.newTraceRepository(dataSource);
    }

    public TraceAttributeRepository traceAttributes() {
        return repositories.newTraceAttributeRepository(dataSource);
    }

    public MethodTraceWeightRepository methodTraceWeights() {
        return repositories.newMethodTraceWeightRepository(dataSource);
    }

    public PipelineRunRepository pipelineRuns() {
        return repositories.newPipelineRunRepository(dataSource);
    }

    public ProfileExceptionRepository exceptions() {
        return repositories.newExceptionRepository(dataSource);
    }
}
