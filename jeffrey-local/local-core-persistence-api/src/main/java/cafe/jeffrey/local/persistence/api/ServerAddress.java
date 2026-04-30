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

package cafe.jeffrey.local.persistence.api;

/**
 * Network coordinates of a connected jeffrey-server.
 *
 * @param plaintext when {@code true}, the gRPC client connects to this address in cleartext h2c
 *                  (no TLS handshake). Defaults to {@code false} — TLS is the secure default and
 *                  matches the existing public-internet workflow. Set {@code true} only for
 *                  in-cluster Service DNS or trusted-LAN setups.
 */
public record ServerAddress(String hostname, int port, boolean plaintext) {

    public ServerAddress(String hostname, int port) {
        this(hostname, port, false);
    }

    @Override
    public String toString() {
        return hostname + ":" + port + (plaintext ? " (plaintext)" : "");
    }
}
