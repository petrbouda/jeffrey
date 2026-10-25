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
 * Network coordinates of a connected jeffrey-hub, reached over gRPC.
 *
 * @param plaintext when {@code true}, the gRPC client connects to this address in cleartext h2c
 *                  (no TLS handshake). Defaults to {@code false} — TLS is the secure default and
 *                  matches the existing public-internet workflow. Set {@code true} only for
 *                  in-cluster Service DNS or trusted-LAN setups.
 */
public record GrpcHubAddress(String hostname, int port, boolean plaintext) implements HubAddress {

    private static final String PORT_SEPARATOR = ":";

    public GrpcHubAddress(String hostname, int port) {
        this(hostname, port, false);
    }

    /** {@code hostname:port}, without {@code plaintext}: two hubs differing only in it are one hub. */
    @Override
    public String identity() {
        return hostname + PORT_SEPARATOR + port;
    }

    /** A hub owns its volume and serves every change to it. */
    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public String toString() {
        return hostname + PORT_SEPARATOR + port + (plaintext ? " (plaintext)" : "");
    }
}
