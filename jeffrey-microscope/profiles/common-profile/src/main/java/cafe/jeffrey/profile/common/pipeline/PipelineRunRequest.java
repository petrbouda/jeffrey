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
 * @param onFinished called once with the terminal result of accepted work, whether it completed or
 *                   failed. A rejected submission throws without calling this callback; the caller
 *                   still owns its resources. A failure here is logged and swallowed, because losing
 *                   the record of a run is not a reason to also lose the run
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
