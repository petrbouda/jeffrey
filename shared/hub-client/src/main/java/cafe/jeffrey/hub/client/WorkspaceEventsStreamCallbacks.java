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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.client.dto.WorkspaceEventsBatch;

import java.util.function.Consumer;

/**
 * Lifecycle handlers for a workspace event subscription.
 *
 * @param onBatch    called for each delivered batch, including heartbeats
 * @param onComplete called when the stream ends without a fault — either the server closed it
 *                   cleanly or the subscription was cancelled. A hub that does not tail (the stub,
 *                   for instance) completes right after catch-up, so callers should treat this as
 *                   "no more events are coming" rather than as a reason to reconnect.
 * @param onError    called when the stream fails
 */
public record WorkspaceEventsStreamCallbacks(
        Consumer<WorkspaceEventsBatch> onBatch,
        Runnable onComplete,
        Consumer<Throwable> onError) {
}
