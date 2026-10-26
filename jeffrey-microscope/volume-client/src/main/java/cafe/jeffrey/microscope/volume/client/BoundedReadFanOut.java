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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.shared.volume.ReadFanOut;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * Reads at most {@link #maxInFlight} items at a time, each on its own virtual thread: the round-trips
 * to an Azure file share of one page overlap instead of queueing. A fan-out from inside one of its
 * reads runs one after another, so the bound holds across nested reads and nothing waits on a slot
 * it holds itself.
 */
public final class BoundedReadFanOut implements ReadFanOut {

    /** Reads of a share in flight at once for one request. */
    public static final int DEFAULT_MAX_IN_FLIGHT = 8;

    private static final ThreadLocal<Boolean> INSIDE_A_READ = ThreadLocal.withInitial(() -> false);

    private final int maxInFlight;

    public BoundedReadFanOut(int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("A fan-out needs at least one read in flight: max=" + maxInFlight);
        }
        this.maxInFlight = maxInFlight;
    }

    @Override
    public <T, R> List<R> map(List<T> items, Function<T, R> read) {
        if (items.size() < 2 || INSIDE_A_READ.get()) {
            return ReadFanOut.sequential().map(items, read);
        }
        Semaphore slots = new Semaphore(maxInFlight);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> pending = new ArrayList<>(items.size());
            for (T item : items) {
                pending.add(executor.submit(() -> readInASlot(slots, item, read)));
            }
            List<R> answers = new ArrayList<>(items.size());
            for (Future<R> answer : pending) {
                answers.add(answerOf(answer));
            }
            return answers;
        }
    }

    private static <T, R> R readInASlot(Semaphore slots, T item, Function<T, R> read) throws InterruptedException {
        slots.acquire();
        INSIDE_A_READ.set(true);
        try {
            return read.apply(item);
        } finally {
            INSIDE_A_READ.remove();
            slots.release();
        }
    }

    private static <R> R answerOf(Future<R> answer) {
        try {
            return answer.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading the volume", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("A volume read failed", e.getCause());
        }
    }
}
