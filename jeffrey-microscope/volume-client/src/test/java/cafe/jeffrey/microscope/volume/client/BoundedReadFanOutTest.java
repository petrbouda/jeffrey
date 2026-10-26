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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads several at a time, never more than its bound, answering in the order asked. */
class BoundedReadFanOutTest {

    private static final int BOUND = 3;

    private final BoundedReadFanOut fanOut = new BoundedReadFanOut(BOUND);

    @Test
    void answersInTheOrderAsked() {
        List<Integer> items = IntStream.range(0, 50).boxed().toList();

        assertEquals(items.stream().map(i -> i * 2).toList(), fanOut.map(items, i -> i * 2));
    }

    @Test
    void neverReadsMoreThanItsBoundAtOnce() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();

        fanOut.map(IntStream.range(0, 30).boxed().toList(), i -> {
            most.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sleepBriefly();
            inFlight.decrementAndGet();
            return i;
        });

        assertTrue(most.get() <= BOUND, "at most " + BOUND + " at once, saw " + most.get());
        assertTrue(most.get() > 1, "the reads overlapped");
    }

    @Test
    void aFailedReadSurfacesAsItWasThrown() {
        UncheckedIOException failure = new UncheckedIOException(new IOException("share unreachable"));

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, () -> fanOut.map(List.of(1, 2, 3), i -> {
            if (i == 2) {
                throw failure;
            }
            return i;
        }));

        assertSame(failure, thrown);
    }

    @Test
    void aFanOutInsideAReadRunsInOrderWithoutWaitingOnItself() {
        List<List<Integer>> nested = fanOut.map(List.of(1, 2, 3, 4, 5, 6),
                i -> fanOut.map(List.of(i, i + 10), j -> j));

        assertEquals(List.of(1, 11), nested.getFirst());
        assertEquals(6, nested.size());
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
