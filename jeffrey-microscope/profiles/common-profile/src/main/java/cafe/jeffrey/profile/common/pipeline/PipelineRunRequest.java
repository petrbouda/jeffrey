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

package cafe.jeffrey.profile.common.pipeline;

import java.util.function.Consumer;

/**
 * Everything one run needs: what it targets, what it does, and where its outcome goes.
 *
 * <p>{@code onFinished} is how persistence stays out of the registry. Both pipelines store their run in
 * the same table, but each opens it against its own profile database, and neither the registry nor the
 * run should know what a {@code DataSource} is.</p>
 *
 * @param key        what the run is filed under; one run per key at a time
 * @param scopeId    what within that key the run targets (an event type, say), or {@code ""}
 * @param work       the pipeline body, which drives the stages
 * @param onFinished called once with the terminal result, whether the run completed or failed; a
 *                   failure here is logged and swallowed, because losing the record of a run is not a
 *                   reason to also lose the run
 */
public record PipelineRunRequest<K>(
        K key,
        String scopeId,
        Consumer<PipelineRun> work,
        Consumer<PipelineRunResult> onFinished) {

    public PipelineRunRequest {
        if (key == null) {
            throw new IllegalArgumentException("Run key must not be null");
        }
        if (work == null) {
            throw new IllegalArgumentException("Run work must not be null");
        }
        scopeId = scopeId == null ? "" : scopeId;
    }

    public static <K> PipelineRunRequest<K> of(K key, Consumer<PipelineRun> work) {
        return new PipelineRunRequest<>(key, "", work, null);
    }
}
