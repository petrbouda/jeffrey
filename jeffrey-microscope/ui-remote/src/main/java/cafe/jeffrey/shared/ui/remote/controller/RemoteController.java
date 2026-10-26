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

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteRegistry;
import cafe.jeffrey.shared.ui.remote.dto.CurrentRemoteResponse;
import cafe.jeffrey.shared.ui.remote.dto.RemoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one configured remote, read-only: configuration ({@code jeffrey.microscope.remote.*}) is the
 * only place it is declared, so there is nothing here to connect, change or disconnect one.
 * Registered through {@code RemoteFeatureConfiguration} over a {@link RemoteRegistry} bridge.
 */
@RestController
@RequestMapping("/api/internal/remote")
public class RemoteController {

    private static final String LOCATION_SEPARATOR = "/";

    private final RemoteRegistry registry;

    public RemoteController(RemoteRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public CurrentRemoteResponse current() {
        return new CurrentRemoteResponse(registry.find().map(RemoteController::toResponse).orElse(null));
    }

    private static RemoteResponse toResponse(RemoteInfo remote) {
        AzureFilesAddress share = remote.address();
        return new RemoteResponse(remote.name(), location(share), share.readOnly());
    }

    private static String location(AzureFilesAddress azure) {
        String share = azure.endpoint() + LOCATION_SEPARATOR + azure.share();
        return azure.rootPath().isEmpty() ? share : share + LOCATION_SEPARATOR + azure.rootPath();
    }
}
