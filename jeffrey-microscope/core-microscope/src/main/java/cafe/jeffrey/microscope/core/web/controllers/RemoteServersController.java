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

package cafe.jeffrey.microscope.core.web.controllers;

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
import cafe.jeffrey.microscope.core.client.RemoteClients;
import cafe.jeffrey.microscope.core.client.RemoteDiscoveryClient;
import cafe.jeffrey.microscope.core.manager.server.RemoteServerManager;
import cafe.jeffrey.microscope.core.manager.server.RemoteServersManager;
import cafe.jeffrey.microscope.core.resources.request.AddRemoteServerRequest;
import cafe.jeffrey.microscope.core.resources.response.RemoteServerResponse;
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.microscope.persistence.api.ServerAddress;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.List;

@RestController
@RequestMapping("/api/internal/remote-servers")
public class RemoteServersController {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteServersController.class);

    private final RemoteServersManager remoteServersManager;
    private final RemoteClients.Factory remoteClientsFactory;

    public RemoteServersController(
            RemoteServersManager remoteServersManager,
            RemoteClients.Factory remoteClientsFactory) {
        this.remoteServersManager = remoteServersManager;
        this.remoteClientsFactory = remoteClientsFactory;
    }

    @GetMapping
    public List<RemoteServerResponse> list() {
        return remoteServersManager.findAll().stream()
                .map(RemoteServerManager::info)
                .map(RemoteServersController::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<RemoteServerResponse> add(@RequestBody AddRemoteServerRequest request) {
        validate(request);

        ServerAddress address = new ServerAddress(request.hostname(), request.port(), request.plaintext());
        // Probe the gRPC endpoint before persisting so the user gets immediate feedback
        // for an unreachable / misconfigured server instead of a stored unusable pointer.
        try {
            RemoteClients clients = remoteClientsFactory.apply(address);
            RemoteDiscoveryClient.PublicApiInfo info = clients.discovery().info();
            LOG.debug("Verified remote server reachability: address={} version={} apiVersion={}",
                    address, info.version(), info.apiVersion());
        } catch (StatusRuntimeException e) {
            String message = switch (e.getStatus().getCode()) {
                case UNAVAILABLE -> "Cannot connect to %s:%d. Check that Jeffrey Server is running and the gRPC port is correct."
                        .formatted(request.hostname(), request.port());
                case DEADLINE_EXCEEDED -> "Connection to %s:%d timed out."
                        .formatted(request.hostname(), request.port());
                case UNKNOWN, UNIMPLEMENTED -> "%s:%d does not appear to be a Jeffrey gRPC service. Check you're connecting to the gRPC port (default 9090), not HTTP."
                        .formatted(request.hostname(), request.port());
                default -> "Failed to reach Jeffrey Server at %s:%d: %s"
                        .formatted(request.hostname(), request.port(), e.getStatus().getCode());
            };
            throw Exceptions.invalidRequest(message);
        }

        RemoteServerManager server = remoteServersManager.create(
                new RemoteServersManager.CreateServerRequest(request.name(), address));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(server.info()));
    }

    @GetMapping("/{serverId}")
    public RemoteServerResponse info(@PathVariable("serverId") String serverId) {
        RemoteServerManager server = remoteServersManager.findById(serverId)
                .orElseThrow(() -> Exceptions.invalidRequest("Remote server not found: " + serverId));
        return toResponse(server.info());
    }

    @DeleteMapping("/{serverId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("serverId") String serverId) {
        RemoteServerManager server = remoteServersManager.findById(serverId)
                .orElseThrow(() -> Exceptions.invalidRequest("Remote server not found: " + serverId));
        server.delete();
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

    private static RemoteServerResponse toResponse(RemoteServerInfo info) {
        return new RemoteServerResponse(
                info.serverId(),
                info.name(),
                info.address().hostname(),
                info.address().port(),
                info.address().plaintext(),
                info.createdAt() != null ? info.createdAt().toEpochMilli() : 0L);
    }
}
