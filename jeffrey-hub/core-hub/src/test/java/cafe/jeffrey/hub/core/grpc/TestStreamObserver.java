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

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Collecting {@link StreamObserver} for server-streaming tests. Messages are recorded in a
 * synchronized list because gRPC delivers them from its own threads, and the latches let a
 * test await a terminal event without sleeping.
 *
 * @param <T> the streamed message type
 */
class TestStreamObserver<T> implements StreamObserver<T> {

    final List<T> messages = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch completeLatch = new CountDownLatch(1);
    final CountDownLatch errorLatch = new CountDownLatch(1);
    volatile Throwable error;

    @Override
    public void onNext(T value) {
        messages.add(value);
    }

    @Override
    public void onError(Throwable t) {
        error = t;
        errorLatch.countDown();
    }

    @Override
    public void onCompleted() {
        completeLatch.countDown();
    }
}
