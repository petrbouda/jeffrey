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

package cafe.jeffrey.hub.client;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.WorkspaceEventBatch;
import cafe.jeffrey.hub.client.WorkspaceEventsClient.WorkspaceEventsSubscription;

import java.util.Set;

/**
 * Translates the gRPC stream into {@link WorkspaceEventsStreamCallbacks} and removes the
 * subscription from the client's registry when the stream reaches a terminal state.
 */
class WorkspaceEventBatchStreamObserver implements StreamObserver<WorkspaceEventBatch> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventBatchStreamObserver.class);

    private final String workspaceId;
    private final WorkspaceEventsSubscription subscription;
    private final Set<WorkspaceEventsSubscription> activeSubscriptions;
    private final WorkspaceEventsStreamCallbacks callbacks;

    WorkspaceEventBatchStreamObserver(
            String workspaceId,
            WorkspaceEventsSubscription subscription,
            Set<WorkspaceEventsSubscription> activeSubscriptions,
            WorkspaceEventsStreamCallbacks callbacks) {

        this.workspaceId = workspaceId;
        this.subscription = subscription;
        this.activeSubscriptions = activeSubscriptions;
        this.callbacks = callbacks;
    }

    @Override
    public void onNext(WorkspaceEventBatch batch) {
        callbacks.onBatch().accept(WorkspaceEventsClient.toBatch(batch));
    }

    @Override
    public void onError(Throwable t) {
        activeSubscriptions.remove(subscription);

        // A cancellation is how a normal unsubscribe surfaces — usually our own cancel() once the
        // consumer went away — so it terminates the stream rather than faulting it. Routing it to
        // onError would make every closed browser tab look like a broken hub connection.
        if (Status.fromThrowable(t).getCode() == Status.Code.CANCELLED) {
            LOG.info("Workspace event stream cancelled: workspaceId={}", workspaceId);
            callbacks.onComplete().run();
            return;
        }

        LOG.warn("Workspace event stream failed: workspaceId={}", workspaceId, t);
        callbacks.onError().accept(t);
    }

    @Override
    public void onCompleted() {
        activeSubscriptions.remove(subscription);
        LOG.info("Workspace event stream completed: workspaceId={}", workspaceId);
        callbacks.onComplete().run();
    }
}
