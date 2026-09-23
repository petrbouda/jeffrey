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

package cafe.jeffrey.microscope.model.hub;

/**
 * Network coordinates of a connected jeffrey-hub.
 *
 * @param plaintext when {@code true}, the gRPC client connects to this address in cleartext h2c
 *                  (no TLS handshake). Defaults to {@code false} — TLS is the secure default and
 *                  matches the existing public-internet workflow. Set {@code true} only for
 *                  in-cluster Service DNS or trusted-LAN setups.
 */
public record HubAddress(String hostname, int port, boolean plaintext) {

    public HubAddress(String hostname, int port) {
        this(hostname, port, false);
    }

    @Override
    public String toString() {
        return hostname + ":" + port + (plaintext ? " (plaintext)" : "");
    }
}
