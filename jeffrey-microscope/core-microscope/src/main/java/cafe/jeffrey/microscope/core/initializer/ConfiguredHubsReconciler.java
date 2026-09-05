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

import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Makes the stored hub registry match {@code jeffrey.microscope.hubs.*} at startup.
 * <p>
 * Runs as a bean init method rather than an {@code ApplicationRunner}: Spring Boot invokes runners
 * after the context has finished refreshing, by which point the HTTP connector is already
 * accepting requests and the UI could observe a half-reconciled registry.
 * <p>
 * No hub is contacted here. A declared hub is persisted whether or not it answers, so a hub that
 * starts after Microscope — the normal case in a compose file or a pod — works as soon as it comes
 * up, instead of being skipped until the next restart.
 */
public class ConfiguredHubsReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredHubsReconciler.class);

    private final HubsRepository repository;
    private final CachedHubClientsFactory clientsFactory;
    private final ConfiguredHubsProperties properties;
    private final ConfiguredHubsPlanner planner;

    public ConfiguredHubsReconciler(
            HubsRepository repository,
            CachedHubClientsFactory clientsFactory,
            ConfiguredHubsProperties properties,
            ConfiguredHubsPlanner planner) {

        this.repository = repository;
        this.clientsFactory = clientsFactory;
        this.properties = properties;
        this.planner = planner;
    }

    public void reconcile() {
        List<DesiredHub> desired = properties.resolve();
        List<HubInfo> actual = repository.findAll();

        // An empty declaration still has work to do when CONFIG rows survive from a previous
        // configuration -- that is what "configuration owns these hubs" means. Only a genuinely
        // empty situation returns early, so an installation that never declares a hub stays silent.
        if (desired.isEmpty() && actual.stream().noneMatch(hub -> hub.source() == HubSource.CONFIG)) {
            return;
        }

        HubReconcilePlan plan = planner.plan(desired, actual);
        if (plan.isEmpty()) {
            LOG.debug("Hub configuration already matches the registry: declared={}", desired.size());
            return;
        }

        apply(plan);

        LOG.info("Reconciled hubs from configuration: added={} updated={} removed={} declared={}",
                plan.inserts().size(), plan.updates().size(), plan.deletes().size(), desired.size());
    }

    /**
     * Deletes first, then updates, then inserts — the order the plan is built for, and the only one
     * that stays legal under {@code UNIQUE (hostname, port)} while addresses move between rows.
     */
    private void apply(HubReconcilePlan plan) {
        for (HubInfo hub : plan.deletes()) {
            repository.delete(hub.hubId());
            evict(hub.address());
            LOG.info("Removed hub no longer declared in configuration: hub_id={} name={} address={}",
                    hub.hubId(), hub.name(), hub.address());
        }

        for (HubReconcilePlan.HubUpdate update : plan.updates()) {
            repository.update(update.target());
            if (!update.previous().address().equals(update.target().address())) {
                evict(update.previous().address());
            }
            LOG.info("Updated hub from configuration: hub_id={} name={} address={}",
                    update.target().hubId(), update.target().name(), update.target().address());
        }

        for (HubInfo hub : plan.inserts()) {
            repository.create(hub);
            evict(hub.address());
            LOG.info("Registered hub from configuration: hub_id={} name={} address={}",
                    hub.hubId(), hub.name(), hub.address());
        }
    }

    /**
     * Drops any cached gRPC channel for an address whose owner changed. The cache is keyed by
     * address rather than by hub id, so a channel opened for the previous occupant would otherwise
     * be handed to the new one.
     */
    private void evict(HubAddress address) {
        clientsFactory.evict(address);
    }
}
