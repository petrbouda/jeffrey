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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.grpc.client.ActivityScanSnapshot;
import cafe.jeffrey.microscope.grpc.client.ActivityScanTarget;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.shared.common.activity.ActivityState;
import io.grpc.Status;

import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A Hub scan seen as one of Microscope's operations, so {@code operations_status} and
 * {@code operations_cancel} work on it the way they work on an import, a download or a heap
 * preparation. Without this a reader would have to know that background work started by
 * {@code hubs_} is the one kind polled through a family-specific pair of tools.
 *
 * <p>The remote call is made <em>only</em> by {@link #snapshot()} and {@link #cancel()}. Lifecycle
 * reads answer from the last observation, because the registry asks every retained entry for its
 * {@code finishedAt} whenever any operation is looked up, and a Hub round trip per entry per lookup
 * is not a thing to hide behind a getter.</p>
 */
final class HubActivityOperation implements OperationHandle<ActivityScanSnapshot> {

    private static final Map<ActivityState, OperationState> STATES = Map.of(
            ActivityState.QUEUED, OperationState.QUEUED,
            ActivityState.RUNNING, OperationState.RUNNING,
            ActivityState.CANCEL_REQUESTED, OperationState.CANCEL_REQUESTED,
            ActivityState.COMPLETED, OperationState.COMPLETED,
            ActivityState.CANCELLED, OperationState.CANCELLED,
            ActivityState.FAILED, OperationState.FAILED);

    private final Supplier<ActivityScanSnapshot> refresh;
    private final Supplier<ActivityScanSnapshot> remoteCancel;
    private volatile ActivityScanSnapshot last;

    HubActivityOperation(
            ActivityScanSnapshot initial,
            Supplier<ActivityScanSnapshot> refresh,
            Supplier<ActivityScanSnapshot> remoteCancel) {
        this.last = initial;
        this.refresh = refresh;
        this.remoteCancel = remoteCancel;
    }

    @Override
    public OperationSnapshot<ActivityScanSnapshot> snapshot() {
        ActivityScanSnapshot current = last;
        if (!STATES.get(current.status()).terminal()) {
            current = observe(fetch(refresh));
        }
        return toOperation(current);
    }

    /** A delayed poll must never move a terminal or cancellation state backwards. */
    synchronized ActivityScanSnapshot observe(ActivityScanSnapshot observation) {
        var target = new ActivityScanTarget(last.workspaceId(), last.projectId(), last.sessionId(), last.scanId());
        if (!observation.describes(target)) {
            throw new IllegalStateException("Hub returned activity for a different scan");
        }
        if (last.status().terminal()
                || (last.status() == ActivityState.CANCEL_REQUESTED && !observation.status().terminal())
                || (last.status() == ActivityState.RUNNING && observation.status() == ActivityState.QUEUED)) {
            return last;
        }
        last = observation;
        return last;
    }

    private ActivityScanSnapshot fetch(Supplier<ActivityScanSnapshot> operation) {
        try {
            return operation.get();
        } catch (RuntimeException error) {
            if (Status.fromThrowable(error).getCode() != Status.Code.NOT_FOUND) {
                throw error;
            }
            // Eviction, expiry and a Hub restart all remove the remote worker. A stale running
            // handle must become terminal locally so normal operation retention can reclaim it.
            return last.failedAt(Instant.now().toEpochMilli(),
                    "Hub scan is no longer available: it expired, was evicted, or the Hub restarted");
        }
    }

    /** The last observation, never a new one — see the class comment. */
    @Override
    public OperationSnapshot<ActivityScanSnapshot> lifecycleSnapshot() {
        return toOperation(last);
    }

    @Override
    public String operationId() {
        return last.scanId();
    }

    @Override
    public Instant startedAt() {
        return Instant.ofEpochMilli(last.startedAt());
    }

    @Override
    public Instant finishedAt() {
        Long finished = last.finishedAt();
        return finished == null ? null : Instant.ofEpochMilli(finished);
    }

    @Override
    public boolean cancel() {
        if (STATES.get(last.status()).terminal()) {
            return false;
        }
        ActivityScanSnapshot cancelled = observe(fetch(remoteCancel));
        return cancelled.status() == ActivityState.CANCEL_REQUESTED;
    }

    private static OperationSnapshot<ActivityScanSnapshot> toOperation(ActivityScanSnapshot scan) {
        Long finished = scan.finishedAt();
        return new OperationSnapshot<>(
                scan.scanId(),
                STATES.get(scan.status()),
                Instant.ofEpochMilli(scan.startedAt()),
                finished == null ? null : Instant.ofEpochMilli(finished),
                scan.status() == ActivityState.CANCEL_REQUESTED,
                "counting",
                Map.of(
                        "filesTotal", scan.filesTotal(),
                        "totalEvents", scan.totalEvents(),
                        "distinctEventTypes", scan.distinctEventTypes(),
                        "coverageKnown", scan.coverageKnown(),
                        "sourceErrors", scan.sourceErrors()),
                scan.status().terminal() ? scan : null,
                scan.error() == null ? null : new IllegalStateException(scan.error()));
    }
}
