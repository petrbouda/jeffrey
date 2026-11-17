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

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.microscope.model.Type;

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
