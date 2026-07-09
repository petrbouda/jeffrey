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

import io.grpc.stub.ServerCallStreamObserver;

import java.util.concurrent.Semaphore;

/**
 * Backpressure gate for a server-streaming call. Pauses the producer when the
 * channel is not ready, resumes when gRPC fires onReady, and short-circuits when
 * the call is cancelled by the client.
 *
 * <p>{@link #attach} must be called on the gRPC handler thread, before the service
 * method returns — gRPC rejects {@code setOnReadyHandler}/{@code setOnCancelHandler}
 * once the {@code StreamObserver} has been handed back.</p>
 */
final class ReadyGate {

    private final ServerCallStreamObserver<?> observer;
    private final Semaphore permits = new Semaphore(0);
    private volatile boolean cancelled = false;

    private ReadyGate(ServerCallStreamObserver<?> observer) {
        this.observer = observer;
    }

    static ReadyGate attach(ServerCallStreamObserver<?> observer) {
        ReadyGate gate = new ReadyGate(observer);
        observer.setOnReadyHandler(gate.permits::release);
        observer.setOnCancelHandler(() -> {
            gate.cancelled = true;
            gate.permits.release();
        });
        return gate;
    }

    boolean isCancelled() {
        return cancelled || observer.isCancelled();
    }

    void awaitReady() throws InterruptedException {
        if (observer.isReady() || isCancelled()) {
            return;
        }
        permits.drainPermits();
        while (!observer.isReady() && !isCancelled()) {
            permits.acquire();
        }
    }
}
