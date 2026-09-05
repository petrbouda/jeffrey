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

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out what has to change for the stored hub registry to match the configuration.
 * <p>
 * Deliberately a pure function of ({@code desired}, {@code actual}) — no database, no Spring, no
 * gRPC — because this is where all the interesting cases live and they deserve to be testable
 * without infrastructure.
 */
public class ConfiguredHubsPlanner {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredHubsPlanner.class);

    private final Clock clock;

    public ConfiguredHubsPlanner(Clock clock) {
        this.clock = clock;
    }

    public HubReconcilePlan plan(List<DesiredHub> desired, List<HubInfo> actual) {
        Map<String, HubInfo> byId = new HashMap<>();
        Map<String, HubInfo> byAddress = new HashMap<>();
        for (HubInfo hub : actual) {
            byId.put(hub.hubId(), hub);
            byAddress.put(addressKey(hub.address()), hub);
        }

        Set<String> handled = new HashSet<>();
        Map<String, HubInfo> deletes = new LinkedHashMap<>();
        List<HubReconcilePlan.HubUpdate> updates = new ArrayList<>();
        List<HubInfo> inserts = new ArrayList<>();

        for (DesiredHub hub : desired) {
            HubInfo match = resolveMatch(hub, byId, byAddress, handled);
            if (match != null) {
                handled.add(match.hubId());
            }

            evictAddressOccupant(hub, byAddress, handled, deletes);

            if (match == null) {
                inserts.add(new HubInfo(
                        hub.hubId(), hub.name(), hub.address(), clock.instant(), HubSource.CONFIG));
                continue;
            }

            // Keeps the stored id and creation time: the id is referenced by the origin.hubId tag
            // written onto every recording downloaded from this hub, so it must survive a rename
            // or a re-point.
            HubInfo target = new HubInfo(
                    match.hubId(), hub.name(), hub.address(), match.createdAt(), HubSource.CONFIG);

            if (!addressKey(match.address()).equals(addressKey(target.address()))) {
                // Moving to a different hostname:port is done as a delete plus an insert rather
                // than an UPDATE. Because every delete is applied before every insert, this is the
                // only formulation that survives two hubs exchanging addresses in one edit, which
                // an in-place UPDATE would fail on the UNIQUE (hostname, port) constraint. The
                // row's id and creation time are carried across explicitly, so nothing is lost,
                // and a crash in between simply leaves the next startup to recreate it from the
                // configuration that owns it.
                deletes.put(match.hubId(), match);
                inserts.add(target);
            } else if (!match.equals(target)) {
                updates.add(new HubReconcilePlan.HubUpdate(match, target));
            }
        }

        sweepRetiredConfigHubs(actual, handled, deletes);

        return new HubReconcilePlan(inserts, updates, List.copyOf(deletes.values()));
    }

    /**
     * Finds the stored row this declaration should take over: first by id, then by address.
     * <p>
     * Matching by address second is what makes an adopted hub stable. A hub added through the UI
     * keeps its generated id when configuration claims its address, so every subsequent startup
     * re-matches the same row by address and finds nothing to do — re-keying it to the derived
     * {@code cfg-} id instead would make the reconcile flip the row on alternating runs.
     */
    private static HubInfo resolveMatch(
            DesiredHub hub,
            Map<String, HubInfo> byId,
            Map<String, HubInfo> byAddress,
            Set<String> handled) {

        HubInfo byIdMatch = byId.get(hub.hubId());
        if (byIdMatch != null && !handled.contains(byIdMatch.hubId())) {
            return byIdMatch;
        }

        HubInfo occupant = byAddress.get(addressKey(hub.address()));
        if (occupant != null && !handled.contains(occupant.hubId())) {
            if (occupant.source() == HubSource.USER) {
                LOG.info("Adopting user-added hub into configuration: hub_id={} config_key={} address={}",
                        occupant.hubId(), hub.key(), occupant.address());
            }
            return occupant;
        }

        return null;
    }

    /**
     * Removes any other row sitting on the address this declaration claims. Without this a hub
     * re-pointed onto an address another hub already holds would fail the unique constraint.
     */
    private static void evictAddressOccupant(
            DesiredHub hub,
            Map<String, HubInfo> byAddress,
            Set<String> handled,
            Map<String, HubInfo> deletes) {

        HubInfo occupant = byAddress.get(addressKey(hub.address()));
        if (occupant == null || handled.contains(occupant.hubId())) {
            return;
        }

        if (occupant.source() == HubSource.USER) {
            LOG.warn("Removing user-added hub occupying an address claimed by configuration: hub_id={} address={} config_key={}",
                    occupant.hubId(), occupant.address(), hub.key());
        }
        deletes.put(occupant.hubId(), occupant);
        handled.add(occupant.hubId());
    }

    /**
     * Configuration owns every CONFIG row, so one that no declaration claims has been retired and
     * must go. USER rows are never swept — those belong to whoever added them.
     */
    private static void sweepRetiredConfigHubs(
            List<HubInfo> actual, Set<String> handled, Map<String, HubInfo> deletes) {

        for (HubInfo hub : actual) {
            if (hub.source() == HubSource.CONFIG && !handled.contains(hub.hubId())) {
                deletes.put(hub.hubId(), hub);
            }
        }
    }

    /**
     * The identity the database actually enforces: {@code UNIQUE (hostname, port)}. {@code
     * plaintext} is deliberately excluded — two hubs differing only in it still collide.
     */
    private static String addressKey(HubAddress address) {
        return address.hostname() + ":" + address.port();
    }
}
