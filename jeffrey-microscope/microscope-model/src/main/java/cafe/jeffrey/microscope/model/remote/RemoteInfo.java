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

package cafe.jeffrey.microscope.model.remote;

import java.time.Instant;

/**
 * The one remote this Microscope is connected to: a Jeffrey Hub, or the recording volume on an
 * Azure file share. Its workspaces and projects are read live from it and never cached here.
 *
 * @param name      what the UI and the MCP answers call it
 * @param address   where it is and what kind it is
 * @param createdAt when it was connected
 * @param source    who owns the connection: {@link RemoteSource#CONFIG} when
 *                  {@code jeffrey.microscope.remote.*} declares it — reconciled on every startup,
 *                  and the UI cannot disconnect it — or {@link RemoteSource#USER} when the UI
 *                  connected it, which the reconciliation leaves alone unless configuration
 *                  declares a remote of its own
 */
public record RemoteInfo(String name, RemoteAddress address, Instant createdAt, RemoteSource source) {

    public RemoteInfo {
        if (name == null || name.isBlank() || address == null || createdAt == null || source == null) {
            throw new IllegalArgumentException("A remote needs a name, an address, a connection time and a source");
        }
    }
}
