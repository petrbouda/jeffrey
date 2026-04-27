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
module cafe.jeffrey.local.profile.flamegraph {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.local.profile.frame.ir;
    requires transitive cafe.jeffrey.local.profile.timeseries;
    requires transitive cafe.jeffrey.local.profile.persistence.api;
    requires cafe.jeffrey.local.profile.parser.jdk;
    requires transitive com.google.protobuf;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.flamegraph;
    exports cafe.jeffrey.flamegraph.api;
    exports cafe.jeffrey.flamegraph.diff;
    exports cafe.jeffrey.flamegraph.provider;
}
