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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;

import java.util.List;
import java.util.Optional;

/**
 * Terminal snapshots of staged background runs for one profile — heap-dump initialization and any
 * future pipeline.
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
