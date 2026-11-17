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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;

import java.util.List;
import java.util.Optional;

/**
 * Terminal snapshots of staged background runs for one profile — heap-dump initialization, profile
 * initialization, and any future pipeline.
 *
 * <p>The repository speaks {@link PipelineRunResult} rather than a storage-shaped row, because there is
 * exactly one thing worth storing about a finished run and inventing a second representation of it
 * would only create two places for the stage list to drift. How the stages become bytes is the
 * implementation's business.</p>
 */
public interface PipelineRunRepository {

    /**
     * The stored run for one pipeline and scope. {@code scopeId} is {@code ""} for a pipeline that runs
     * once per profile.
     */
    Optional<PipelineRunResult> find(String pipelineId, String scopeId);

    /** Every stored run of one pipeline, for pipelines that run per scope (e.g. per event type). */
    List<PipelineRunResult> findAll(String pipelineId);

    /**
     * Stores a finished run, replacing any previous one for the same pipeline and scope. A re-run
     * supersedes its predecessor entirely: keeping both would present two contradictory accounts of the
     * same work as if both were current.
     */
    void upsert(PipelineRunResult run);

    void deleteAll(String pipelineId);
}
