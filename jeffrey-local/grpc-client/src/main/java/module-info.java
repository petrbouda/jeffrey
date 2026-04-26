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

module pbouda.jeffrey.local.grpc.client {
    requires transitive pbouda.jeffrey.server.api;
    requires transitive pbouda.jeffrey.local.persistence.api;
    requires transitive io.grpc;
    requires io.grpc.stub;
    requires io.grpc.netty.shaded;
    requires org.slf4j;

    exports pbouda.jeffrey.local.core.client;
}
