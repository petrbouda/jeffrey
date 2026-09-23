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

package cafe.jeffrey.provider.profile.jdbc;

import java.util.concurrent.Semaphore;

/**
 * How many batches one profile's ingest may have in flight at once.
 * <p>
 * The writers hand every filled batch to the shared database-writer pool and carry straight on, so
 * nothing connects the rate events are parsed at to the rate they are written at. While the disk
 * keeps up that is exactly what you want. When it does not — a slow or contended volume, a profile
 * whose events are unusually wide — the backlog has nowhere to go but the heap, and an ingest that
 * should have run slower fails instead.
 * <p>
 * Taking a slot before submitting turns that into what it should have been: the parser thread waits
 * for the writers. Slots are released on the writer pool, which is a different pool from the one the
 * parser threads run on, so a parser waiting here can never be waiting on itself.
 */
public final class BatchFlushLimit {

    private final Semaphore slots;

    private BatchFlushLimit(int permits) {
        this.slots = new Semaphore(permits);
    }

    /**
     * @param permits how many batches may be queued or running at once; must be positive
     */
    public static BatchFlushLimit ofSlots(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("At least one flush slot is required: permits=" + permits);
        }
        return new BatchFlushLimit(permits);
    }

    /**
     * Waits for a slot. Interruption is restored on the thread and surfaced, because a parser thread
     * that stops waiting without a slot would defeat the bound it is here to enforce.
     */
    public void acquire() {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a batch flush slot", e);
        }
    }

    public void release() {
        slots.release();
    }
}
