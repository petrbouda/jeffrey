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
