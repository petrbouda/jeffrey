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
