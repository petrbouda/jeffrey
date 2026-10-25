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

package cafe.jeffrey.shared.ui.remote.controller;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteRegistry;
import cafe.jeffrey.shared.ui.remote.dto.CurrentRemoteResponse;
import cafe.jeffrey.shared.ui.remote.dto.RemoteResponse;
import cafe.jeffrey.shared.ui.remote.request.ConnectHubRequest;


/**
 * The one connected remote: what it is, connecting a Jeffrey Hub when none is, and disconnecting
 * the one the UI connected. Registered through {@code RemoteFeatureConfiguration} over a
 * {@link RemoteRegistry} bridge and a {@link RemoteClients.Factory} for the reachability probe.
 */
@RestController
@RequestMapping("/api/internal/remote")
public class RemoteController {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteController.class);

    private static final String CONFIGURED_REMOTE_PROPERTY = "jeffrey.microscope.remote";
    private static final String KIND_GRPC = "GRPC";
    private static final String KIND_AZURE_FILES = "AZURE_FILES";
    private static final String LOCATION_SEPARATOR = "/";
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final RemoteRegistry registry;
    private final RemoteClients.Factory clientsFactory;

    public RemoteController(RemoteRegistry registry, RemoteClients.Factory clientsFactory) {
        this.registry = registry;
        this.clientsFactory = clientsFactory;
    }

    @GetMapping
    public CurrentRemoteResponse current() {
        return new CurrentRemoteResponse(registry.find().map(RemoteController::toResponse).orElse(null));
    }

    /**
     * Connects a Jeffrey Hub, probed before it is stored so a mistyped address is reported now
     * rather than kept as an unusable connection. Refused while any remote is connected.
     */
    @PostMapping
    public ResponseEntity<RemoteResponse> connect(@RequestBody ConnectHubRequest request) {
        validate(request);
        registry.find().ifPresent(RemoteController::refuseSecondRemote);

        HubAddress address = new HubAddress(request.hostname().trim(), request.port(), request.plaintext());
        try {
            DiscoveryClient.PublicApiInfo info = clientsFactory.apply(address).discovery().info();
            LOG.debug("Verified the Jeffrey Hub is reachable: address={} version={}", address, info.version());
        } catch (StatusRuntimeException e) {
            // The probe opened a connection to an address that is not becoming the remote; holding
            // it would keep a channel open to a mistyped address for the life of the process
            clientsFactory.evict(address);
            throw Exceptions.invalidRequest(unreachable(request, e));
        }

        RemoteInfo connected = registry.connect(request.name(), address);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(connected));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        RemoteInfo remote = registry.find()
                .orElseThrow(() -> Exceptions.resourceNotFound("No remote is connected"));
        // Disconnecting it would only last until the next startup, which connects it again from
        // the configuration that owns it -- so refuse with the instruction that actually works
        if (remote.source() == RemoteSource.CONFIG) {
            throw Exceptions.invalidRequest(
                    "The remote '%s' is declared in configuration and cannot be disconnected here. Remove %s.* and restart."
                            .formatted(remote.name(), CONFIGURED_REMOTE_PROPERTY));
        }
        registry.disconnect();
    }

    private static void refuseSecondRemote(RemoteInfo connected) {
        String suffix = connected.source() == RemoteSource.CONFIG
                ? " It is declared in " + CONFIGURED_REMOTE_PROPERTY + ".*."
                : " Disconnect it first.";
        throw Exceptions.invalidRequest("Already connected to the remote '%s'.%s".formatted(connected.name(), suffix));
    }

    private static String unreachable(ConnectHubRequest request, StatusRuntimeException e) {
        return switch (e.getStatus().getCode()) {
            case UNAVAILABLE -> "Cannot connect to %s:%d. Check that Jeffrey Hub is running and the gRPC port is correct."
                    .formatted(request.hostname(), request.port());
            case DEADLINE_EXCEEDED -> "Connection to %s:%d timed out."
                    .formatted(request.hostname(), request.port());
            case UNKNOWN, UNIMPLEMENTED -> "%s:%d does not appear to be a Jeffrey gRPC service. Check you're connecting to the gRPC port (default 9090), not HTTP."
                    .formatted(request.hostname(), request.port());
            default -> "Failed to reach Jeffrey Hub at %s:%d: %s"
                    .formatted(request.hostname(), request.port(), e.getStatus().getCode());
        };
    }

    private static void validate(ConnectHubRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("A name is required");
        }
        if (request.hostname() == null || request.hostname().isBlank()) {
            throw Exceptions.invalidRequest("Hostname is required");
        }
        if (request.port() < MIN_PORT || request.port() > MAX_PORT) {
            throw Exceptions.invalidRequest("Port must be between %d and %d".formatted(MIN_PORT, MAX_PORT));
        }
    }

    private static RemoteResponse toResponse(RemoteInfo remote) {
        long createdAt = remote.createdAt().toEpochMilli();
        RemoteAddress address = remote.address();
        return switch (address) {
            case HubAddress hub -> new RemoteResponse(
                    remote.name(), hub.hostname(), hub.port(), hub.plaintext(), createdAt,
                    remote.source().name(), KIND_GRPC, hub.identity(), hub.readOnly());
            case AzureFilesAddress azure -> new RemoteResponse(
                    remote.name(), null, 0, false, createdAt,
                    remote.source().name(), KIND_AZURE_FILES, location(azure), azure.readOnly());
        };
    }

    private static String location(AzureFilesAddress azure) {
        String share = azure.endpoint() + LOCATION_SEPARATOR + azure.share();
        return azure.rootPath().isEmpty() ? share : share + LOCATION_SEPARATOR + azure.rootPath();
    }
}
