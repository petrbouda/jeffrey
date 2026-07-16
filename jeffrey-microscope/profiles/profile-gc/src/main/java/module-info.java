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
module cafe.jeffrey.microscope.profile.gc {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.profile.common;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.microscope.profile.timeseries;
    requires cafe.jeffrey.microscope.profile.parser.api;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires tools.jackson.databind;
    requires HdrHistogram;

    exports cafe.jeffrey.profile.manager.gc;
    exports cafe.jeffrey.profile.manager.gc.builder;
    exports cafe.jeffrey.profile.manager.model.gc;
    exports cafe.jeffrey.profile.manager.model.gc.configuration;
    exports cafe.jeffrey.profile.manager.model.gc.finalizer;
    exports cafe.jeffrey.profile.manager.model.gc.g1;
    exports cafe.jeffrey.profile.manager.model.gc.tables;
    exports cafe.jeffrey.profile.manager.model.gc.tuning;
    exports cafe.jeffrey.profile.manager.model.gc.zgc;
}
