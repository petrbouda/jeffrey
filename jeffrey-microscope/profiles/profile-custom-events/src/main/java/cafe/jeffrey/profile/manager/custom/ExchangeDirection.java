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

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.shared.common.model.Type;

import java.util.Locale;

/**
 * Which side of a call an exchange dashboard reports on: what this application <em>served</em>, or
 * what it <em>called out to</em>.
 * <p>
 * The two are different questions with different answers. A slow inbound endpoint is this
 * application's problem; a slow outbound call is somebody else's, and the only thing this
 * application can do about it is call less often or stop waiting for it. Reporting them together
 * would average a dependency's latency into your own.
 */
public enum ExchangeDirection {

    /** Requests this application received and answered. */
    SERVER(Type.HTTP_SERVER_EXCHANGE, Type.GRPC_SERVER_EXCHANGE),

    /** Requests this application made to somebody else. */
    CLIENT(Type.HTTP_CLIENT_EXCHANGE, Type.GRPC_CLIENT_EXCHANGE);

    private final Type httpEventType;
    private final Type grpcEventType;

    ExchangeDirection(Type httpEventType, Type grpcEventType) {
        this.httpEventType = httpEventType;
        this.grpcEventType = grpcEventType;
    }

    public Type httpEventType() {
        return httpEventType;
    }

    public Type grpcEventType() {
        return grpcEventType;
    }

    /**
     * Parses the value the UI and the MCP tools use, case-insensitively. An unknown one is refused by
     * name rather than silently falling back to SERVER, which would answer a client question with
     * server figures.
     */
    public static ExchangeDirection from(String value) {
        if (value == null || value.isBlank()) {
            return SERVER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown direction '" + value + "'. Valid directions: SERVER, CLIENT");
        }
    }
}
