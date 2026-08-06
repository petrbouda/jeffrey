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

package cafe.jeffrey.profile.common.pipeline;

import java.util.concurrent.Semaphore;

/**
 * The concurrency ceiling of a {@link PipelineRunRegistry}, as a permit count that can change while
 * runs are in flight.
 *
 * <p>Every run takes exactly one permit and gives it back, with no "is there a ceiling?" branch on
 * either side. An unbounded pipeline is simply one holding {@link PipelineRunOptions#UNBOUNDED}
 * permits — a number no realistic fan-out reaches. That matters once the ceiling is editable: a
 * conditional acquire would have to answer "was there a ceiling when this run started?", and a run
 * that acquired under one setting could release under another.</p>
 *
 * <p>Shrinking below the number of permits currently held is allowed and leaves the semaphore
 * temporarily in deficit — held permits are not recalled mid-run, they simply stop being reissued as
 * runs finish, so the new ceiling takes hold as the in-flight work drains.</p>
 */
final class PipelineSlots {

    /**
     * {@link Semaphore#reducePermits(int)} is protected, so shrinking needs a subclass. Nothing else
     * about the semaphore changes.
     */
    private static final class ResizableSemaphore extends Semaphore {

        private ResizableSemaphore(int permits) {
            super(permits, true);
        }

        private void reduce(int reduction) {
            reducePermits(reduction);
        }
    }

    private final ResizableSemaphore semaphore;

    /** The configured ceiling, which is not the semaphore's live permit count while runs hold slots. */
    private int permits;

    PipelineSlots(int permits) {
        this.permits = requireValid(permits);
        this.semaphore = new ResizableSemaphore(permits);
    }

    void acquire() throws InterruptedException {
        semaphore.acquire();
    }

    void release() {
        semaphore.release();
    }

    /** Slots free right now — for logging; it can read negative after the ceiling is lowered. */
    int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Changes the ceiling. Growing hands out the difference immediately, so a queued run can start
     * without waiting for anything to finish.
     *
     * @return true when the ceiling actually changed
     */
    synchronized boolean resize(int newPermits) {
        int target = requireValid(newPermits);
        int delta = target - permits;
        if (delta == 0) {
            return false;
        }

        permits = target;
        if (delta > 0) {
            semaphore.release(delta);
        } else {
            semaphore.reduce(-delta);
        }
        return true;
    }

    synchronized int permits() {
        return permits;
    }

    private static int requireValid(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("At least one concurrent run must be allowed: " + permits);
        }
        return permits;
    }
}
