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
