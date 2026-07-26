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

import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.shared.persistentqueue.QueueEntry;

import java.time.Instant;
import java.util.List;

/**
 * Decorates the workspace event queue so every append wakes up live subscribers.
 *
 * <p>The decorator sits on the <b>queue</b> rather than on {@link WorkspaceEventPublisher}
 * on purpose: {@code WorkspaceEventsReplicatorJob} appends to the queue directly, so a
 * publisher-level hook would miss every CLI-originated event — project created, instance
 * created, session created, session deleted — which is most of what the event log shows.
 * The queue is the only complete choke point.
 *
 * <p>Appends made inside a transaction notify before the commit is visible, so a subscriber
 * may drain and find nothing. That is harmless by design: the subscriber's periodic backstop
 * drain picks the event up shortly afterwards.
 */
public class NotifyingWorkspaceEventQueue implements PersistentQueue<WorkspaceEvent> {

    private final PersistentQueue<WorkspaceEvent> delegate;
    private final WorkspaceEventNotifier notifier;

    public NotifyingWorkspaceEventQueue(
            PersistentQueue<WorkspaceEvent> delegate, WorkspaceEventNotifier notifier) {

        this.delegate = delegate;
        this.notifier = notifier;
    }

    @Override
    public void append(String scopeId, WorkspaceEvent event) {
        delegate.append(scopeId, event);
        notifier.onAppended(scopeId);
    }

    @Override
    public void appendBatch(String scopeId, List<WorkspaceEvent> events) {
        delegate.appendBatch(scopeId, events);
        if (!events.isEmpty()) {
            notifier.onAppended(scopeId);
        }
    }

    @Override
    public List<QueueEntry<WorkspaceEvent>> poll(String scopeId, String consumerId) {
        return delegate.poll(scopeId, consumerId);
    }

    @Override
    public void acknowledge(String scopeId, String consumerId, long offset) {
        delegate.acknowledge(scopeId, consumerId, offset);
    }

    @Override
    public List<QueueEntry<WorkspaceEvent>> findAll(String scopeId, int limit) {
        return delegate.findAll(scopeId, limit);
    }

    @Override
    public List<QueueEntry<WorkspaceEvent>> findFromOffset(String scopeId, long fromOffset, int limit) {
        return delegate.findFromOffset(scopeId, fromOffset, limit);
    }

    @Override
    public long count(String scopeId) {
        return delegate.count(scopeId);
    }

    @Override
    public int deleteEventsOlderThan(Instant cutoff) {
        return delegate.deleteEventsOlderThan(cutoff);
    }
}
