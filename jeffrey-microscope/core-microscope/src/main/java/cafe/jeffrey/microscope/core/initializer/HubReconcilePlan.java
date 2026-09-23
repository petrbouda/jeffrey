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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.microscope.model.hub.HubInfo;

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
