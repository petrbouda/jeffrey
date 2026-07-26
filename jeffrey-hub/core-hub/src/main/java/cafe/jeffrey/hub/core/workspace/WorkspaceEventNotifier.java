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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for "workspace W may have new events" wakeups.
 *
 * <p>The notification deliberately carries <b>no payload</b>. An append cannot supply one:
 * the sequence-assigned offset is not returned by the insert, and the queue inserts with
 * {@code ON CONFLICT DO NOTHING}, so an append may legitimately store nothing at all. A
 * payload-carrying notification would therefore emit phantom events for every
 * deduplication-suppressed candidate. Listeners re-read the queue by offset instead, which
 * also makes a dropped notification cost latency rather than data.
 *
 * <p>This class intentionally has no dependencies. It is what breaks the otherwise circular
 * bean graph (streaming manager needs the reader, which needs the queue, which needs to
 * notify the streaming manager).
 */
public class WorkspaceEventNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventNotifier.class);

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /**
     * Signals that the given workspace may have gained events. Each listener is guarded
     * individually: a misbehaving subscriber must never break the append that triggered it.
     */
    public void onAppended(String workspaceId) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(workspaceId);
            } catch (Exception e) {
                LOG.warn("Workspace event listener failed, ignoring: workspace_id={}", workspaceId, e);
            }
        }
    }
}
