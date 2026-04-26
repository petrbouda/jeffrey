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

module pbouda.jeffrey.profile.common {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive com.fasterxml.jackson.annotation;
    requires org.slf4j;

    exports pbouda.jeffrey.profile.common.analysis;
    exports pbouda.jeffrey.profile.common.analysis.marker;
    exports pbouda.jeffrey.profile.common.config;
    exports pbouda.jeffrey.profile.common.event;
    exports pbouda.jeffrey.profile.common.model;
    exports pbouda.jeffrey.profile.common.treetable;
}
