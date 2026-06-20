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

package cafe.jeffrey.shared.ui.workspace.controller;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.persistence.api.HubAddress;
import cafe.jeffrey.microscope.persistence.api.HubInfo;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;
import cafe.jeffrey.shared.ui.workspace.dto.HubResponse;
import cafe.jeffrey.shared.ui.workspace.request.AddRemoteServerRequest;

import java.util.List;

/**
 * Shared WorkspaceBrowser controller for the connected jeffrey-hub registry. Both deployments
 * register it via {@code WorkspacesFeatureConfiguration} and supply a {@link HubRegistry} bridge over
 * their own {@code HubsManager} plus a {@link HubClients.Factory} for the reachability probe.
 */
@RestController
@RequestMapping("/api/internal/hubs")
public class HubsController {

    private static final Logger LOG = LoggerFactory.getLogger(HubsController.class);

    private final HubRegistry hubRegistry;
    private final HubClients.Factory clientsFactory;

    public HubsController(HubRegistry hubRegistry, HubClients.Factory clientsFactory) {
        this.hubRegistry = hubRegistry;
        this.clientsFactory = clientsFactory;
    }

    @GetMapping
    public List<HubResponse> list() {
        return hubRegistry.findAll().stream()
                .map(HubsController::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<HubResponse> add(@RequestBody AddRemoteServerRequest request) {
        validate(request);

        HubAddress address = new HubAddress(request.hostname(), request.port(), request.plaintext());
        // Probe the gRPC endpoint before persisting so the user gets immediate feedback
        // for an unreachable / misconfigured server instead of a stored unusable pointer.
        try {
            DiscoveryClient.PublicApiInfo info = clientsFactory.apply(address).discovery().info();
            LOG.debug("Verified hub reachability: address={} version={} apiVersion={}",
                    address, info.version(), info.apiVersion());
        } catch (StatusRuntimeException e) {
            String message = switch (e.getStatus().getCode()) {
                case UNAVAILABLE -> "Cannot connect to %s:%d. Check that Jeffrey Hub is running and the gRPC port is correct."
                        .formatted(request.hostname(), request.port());
                case DEADLINE_EXCEEDED -> "Connection to %s:%d timed out."
                        .formatted(request.hostname(), request.port());
                case UNKNOWN, UNIMPLEMENTED -> "%s:%d does not appear to be a Jeffrey gRPC service. Check you're connecting to the gRPC port (default 9090), not HTTP."
                        .formatted(request.hostname(), request.port());
                default -> "Failed to reach Jeffrey Hub at %s:%d: %s"
                        .formatted(request.hostname(), request.port(), e.getStatus().getCode());
            };
            throw Exceptions.invalidRequest(message);
        }

        HubInfo created = hubRegistry.create(request.name(), address);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{hubId}")
    public HubResponse info(@PathVariable("hubId") String hubId) {
        HubInfo info = hubRegistry.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
        return toResponse(info);
    }

    @DeleteMapping("/{hubId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("hubId") String hubId) {
        hubRegistry.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
        hubRegistry.delete(hubId);
    }

    private static void validate(AddRemoteServerRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Server name is required");
        }
        if (request.hostname() == null || request.hostname().isBlank()) {
            throw Exceptions.invalidRequest("Hostname is required");
        }
        if (request.port() < 1 || request.port() > 65535) {
            throw Exceptions.invalidRequest("Port must be between 1 and 65535");
        }
    }

    private static HubResponse toResponse(HubInfo info) {
        return new HubResponse(
                info.hubId(),
                info.name(),
                info.address().hostname(),
                info.address().port(),
                info.address().plaintext(),
                info.createdAt() != null ? info.createdAt().toEpochMilli() : 0L);
    }
}
