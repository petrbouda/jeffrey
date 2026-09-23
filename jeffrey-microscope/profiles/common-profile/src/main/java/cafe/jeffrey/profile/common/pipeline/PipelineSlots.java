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
