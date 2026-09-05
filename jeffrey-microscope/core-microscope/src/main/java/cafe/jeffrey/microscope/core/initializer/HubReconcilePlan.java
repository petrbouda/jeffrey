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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.shared.common.model.hub.HubInfo;

import java.util.List;

/**
 * The writes needed to make the stored hub registry match the configuration.
 * <p>
 * Applied in the order {@code deletes} → {@code updates} → {@code inserts}. That order is what
 * keeps every step legal under the {@code UNIQUE (hostname, port)} constraint on the hubs table:
 * any row that is giving up an address is removed before anything claims it.
 */
public record HubReconcilePlan(
        List<HubInfo> inserts,
        List<HubUpdate> updates,
        List<HubInfo> deletes) {

    /**
     * A row changing in place.
     *
     * @param previous the stored row, kept so the executor knows which gRPC channel to evict
     * @param target   what it should become; carries the same {@code hubId} and {@code createdAt}
     */
    public record HubUpdate(HubInfo previous, HubInfo target) {
    }

    public boolean isEmpty() {
        return inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty();
    }
}
