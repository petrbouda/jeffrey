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

package cafe.jeffrey.profile.common.operation;

import java.time.Instant;

/** Targets exactly one attempt, including after another attempt replaces it for the same key. */
public interface OperationHandle<V> {

    OperationSnapshot<V> snapshot();

    /** Reads lifecycle state without invoking expensive progress providers. */
    default OperationSnapshot<V> lifecycleSnapshot() {
        return snapshot();
    }

    /** Lifecycle metadata must be readable without evaluating a progress supplier. */
    default String operationId() {
        return snapshot().operationId();
    }

    default Instant startedAt() {
        return snapshot().startedAt();
    }

    default Instant finishedAt() {
        return snapshot().finishedAt();
    }

    /** Requests cancellation; false when terminal or already requested. Never waits for the worker. */
    boolean cancel();
}
