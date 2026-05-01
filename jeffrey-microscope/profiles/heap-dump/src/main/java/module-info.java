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
module cafe.jeffrey.microscope.profile.heapdump {
    requires transitive cafe.jeffrey.shared.common;
    requires cafe.jeffrey.microscope.profile.common;
    requires org.openjdk.nashorn;
    requires transitive org.netbeans.lib.profiler.RELEASE250;
    requires org.netbeans.modules.profiler.oql.RELEASE250;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.profile.heapdump;
    exports cafe.jeffrey.profile.heapdump.analyzer;
    exports cafe.jeffrey.profile.heapdump.model;
    exports cafe.jeffrey.profile.heapdump.sanitizer;
}
