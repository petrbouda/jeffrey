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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteRegistry;

import java.util.Optional;

/** Microscope's {@link RemoteRegistry} bridge over its {@link RemoteManager}. */
public class MicroscopeRemoteRegistry implements RemoteRegistry {

    private final RemoteManager remoteManager;

    public MicroscopeRemoteRegistry(RemoteManager remoteManager) {
        this.remoteManager = remoteManager;
    }

    @Override
    public Optional<RemoteInfo> find() {
        return remoteManager.info();
    }
}
