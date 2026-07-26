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

package cafe.jeffrey.hub.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives one live subscription: drains the backlog from the subscription's offset, then
 * tails, waking on notifications and on a periodic backstop.
 *
 * <p>The backstop is what makes a missed notification harmless. Notifications can be lost
 * legitimately — an append inside a transaction fires before its commit is visible — so the
 * loop re-drains every {@code backstopInterval} regardless of whether it was woken.
 */
class WorkspaceEventSubscriber implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventSubscriber.class);

    private final WorkspaceEventSubscription subscription;
    private final WorkspaceEventCallbacks callbacks;
    private final WorkspaceEventReader reader;
    private final Duration backstopInterval;

    /** Binary wakeup latch: permits are drained before each pass, so they never accumulate. */
    private final Semaphore wakeup = new Semaphore(0);
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile Thread worker;
    private long cursor;
    private boolean caughtUpSent;

    WorkspaceEventSubscriber(
            WorkspaceEventSubscription subscription,
            WorkspaceEventCallbacks callbacks,
            WorkspaceEventReader reader,
            Duration backstopInterval) {

        this.subscription = subscription;
        this.callbacks = callbacks;
        this.reader = reader;
        this.backstopInterval = backstopInterval;
        this.cursor = subscription.fromOffset();
    }

    void run() {
        this.worker = Thread.currentThread();
        try {
            while (running.get()) {
                // Drain permits before the pass, not after: a notification that arrives while
                // this pass is reading is already covered by the pass, and one that arrives
                // after it must still wake the next pass.
                wakeup.drainPermits();
                drain();

                if (!caughtUpSent) {
                    caughtUpSent = true;
                    emit(WorkspaceEventPage.heartbeat(cursor));
                }

                boolean woken = awaitWakeup();
                if (!woken && running.get() && subscription.sendEmptyBatches()) {
                    emit(WorkspaceEventPage.heartbeat(cursor));
                }
            }
            callbacks.onComplete().run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callbacks.onComplete().run();
        } catch (Exception e) {
            LOG.warn("Workspace event subscription failed: subscription={}", subscription, e);
            callbacks.onError().accept(e);
        } finally {
            callbacks.onClose().run();
        }
    }

    /**
     * Reads forward until the queue returns nothing. An empty read is the caught-up signal.
     */
    private void drain() {
        while (running.get()) {
            List<WorkspaceEvent> page =
                    reader.findFromOffset(subscription.workspaceId(), cursor, subscription.batchSize());
            if (page.isEmpty()) {
                return;
            }

            // Advance on the raw offset, before filtering. A subscriber watching one event type
            // must still move past events it filtered out, or every reconnect rescans them and
            // its resume offset never progresses.
            cursor = page.getLast().eventId();

            List<WorkspaceEvent> accepted = page.stream()
                    .filter(event -> subscription.accepts(event.eventType()))
                    .toList();

            if (!accepted.isEmpty()) {
                emit(new WorkspaceEventPage(accepted, cursor, caughtUpSent));
            }
        }
    }

    private void emit(WorkspaceEventPage page) {
        callbacks.onNext().accept(page);
    }

    private boolean awaitWakeup() throws InterruptedException {
        return wakeup.tryAcquire(backstopInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Signals that the subscription's workspace may have new events.
     */
    void wakeUp() {
        wakeup.release();
    }

    String workspaceId() {
        return subscription.workspaceId();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        wakeup.release();
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }
}
