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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.common.pipeline.PipelineState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpInitServiceLeaseTest {

    @Mock
    HeapDumpManager manager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void holdsTheLeaseUntilResultStorageFinishes(boolean singleReport) throws Exception {
        HeapDumpInitService service = new HeapDumpInitService(Clock.systemUTC());
        CountDownLatch storing = new CountDownLatch(1);
        CountDownLatch finishStoring = new CountDownLatch(1);
        AtomicInteger released = new AtomicInteger();
        doAnswer(invocation -> {
            storing.countDown();
            assertTrue(finishStoring.await(5, TimeUnit.SECONDS));
            return null;
        }).when(manager).storeInitPipelineResult(any());

        try {
            boolean started = singleReport
                    ? service.startReport("p", manager, "strings", null, released::incrementAndGet)
                    : service.start("p", manager, null, released::incrementAndGet);
            assertTrue(started);
            assertTrue(storing.await(5, TimeUnit.SECONDS));
            assertEquals(0, released.get());
        } finally {
            finishStoring.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, released.get()));
    }

    @Test
    void releasesTheLeaseAfterWorkAndResultStorageFail() {
        HeapDumpInitService service = new HeapDumpInitService(Clock.systemUTC());
        AtomicInteger released = new AtomicInteger();
        when(manager.initialize(eq(null), any())).thenThrow(new IllegalStateException("index failed"));
        doThrow(new IllegalStateException("storage failed")).when(manager).storeInitPipelineResult(any());

        assertTrue(service.start("p", manager, null, released::incrementAndGet));
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, released.get()));
        assertEquals(PipelineState.FAILED, service.progress("p").state());
    }

    @Test
    void doesNotTakeOwnershipOfALeaseWhenTheRunAlreadyExists() throws Exception {
        HeapDumpInitService service = new HeapDumpInitService(Clock.systemUTC());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger firstReleased = new AtomicInteger();
        AtomicInteger secondReleased = new AtomicInteger();
        when(manager.initialize(eq(null), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            return null;
        });
        try {
            assertTrue(service.start("p", manager, null, firstReleased::incrementAndGet));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(service.start("p", manager, null, secondReleased::incrementAndGet));
            assertEquals(0, secondReleased.get());
        } finally {
            finish.countDown();
        }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, firstReleased.get()));
        assertEquals(0, secondReleased.get());
    }
}
