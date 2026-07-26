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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Context;
import io.grpc.stub.ServerCallStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared plumbing for long-lived server-streaming RPCs: cancellation-driven cleanup and
 * backpressured delivery. Both live in one place because the ordering constraints around
 * {@link ReadyGate} and gRPC's cancellation context are subtle enough that a second copy
 * would drift.
 */
final class GrpcStreams {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcStreams.class);

    private GrpcStreams() {
    }

    /**
     * Runs the given cleanup when the client disconnects (gRPC context cancellation),
     * releasing the subscription and its resources.
     */
    static void unsubscribeOnDisconnect(String streamKind, Object subscription, Runnable unsubscribe) {
        Context.current().addListener(_ -> {
            LOG.info("Client disconnected, closing {} stream: subscription={}", streamKind, subscription);
            unsubscribe.run();
        }, Runnable::run);
    }

    /**
     * Delivers one message, pausing the producing thread until the channel can accept more
     * data. Messages produced after cancellation are dropped rather than queued.
     */
    static <T> void sendWithBackpressure(ServerCallStreamObserver<T> observer, ReadyGate gate, T message) {
        try {
            gate.awaitReady();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!gate.isCancelled()) {
            observer.onNext(message);
        }
    }
}
