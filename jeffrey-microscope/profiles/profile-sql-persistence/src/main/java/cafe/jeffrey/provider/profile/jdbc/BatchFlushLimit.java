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
