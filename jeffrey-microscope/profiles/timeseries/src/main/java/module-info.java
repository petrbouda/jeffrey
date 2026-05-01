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
module cafe.jeffrey.microscope.profile.timeseries {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.profile.common;
    requires transitive cafe.jeffrey.microscope.profile.frame.ir;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires cafe.jeffrey.microscope.profile.parser.db;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires tools.jackson.databind;

    exports cafe.jeffrey.timeseries;
}
