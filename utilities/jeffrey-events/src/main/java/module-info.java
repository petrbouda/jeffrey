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

module cafe.jeffrey.jfr.events {
    requires jdk.jfr;

    exports cafe.jeffrey.jfr.events;
    exports cafe.jeffrey.jfr.events.grpc;
    exports cafe.jeffrey.jfr.events.http;
    exports cafe.jeffrey.jfr.events.heartbeat;
    exports cafe.jeffrey.jfr.events.message;
    exports cafe.jeffrey.jfr.events.jdbc.pool;
    exports cafe.jeffrey.jfr.events.jdbc.statement;
}
