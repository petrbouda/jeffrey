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

module pbouda.jeffrey.flamegraph {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.jfrparser.jdk;
    requires transitive pbouda.jeffrey.profile.persistence.api;
    requires transitive pbouda.jeffrey.frameir;
    requires transitive pbouda.jeffrey.timeseries;
    requires transitive com.google.protobuf;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports pbouda.jeffrey.flamegraph;
    exports pbouda.jeffrey.flamegraph.api;
    exports pbouda.jeffrey.flamegraph.diff;
    exports pbouda.jeffrey.flamegraph.provider;
    exports pbouda.jeffrey.flamegraph.proto;
}
