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

/**
 * Declared as a real module rather than an automatic one because Jeffrey's own gRPC client is a
 * named module and has to {@code requires} this by name.
 */
module cafe.jeffrey.jfr.events.grpc.interceptor {
    // The interceptors drive the JFR event lifecycle directly (isEnabled/begin/end/shouldCommit).
    requires jdk.jfr;
    requires cafe.jeffrey.jfr.events;
    requires io.grpc;
    requires com.google.protobuf;

    exports cafe.jeffrey.jfr.events.grpc.interceptor;
}
