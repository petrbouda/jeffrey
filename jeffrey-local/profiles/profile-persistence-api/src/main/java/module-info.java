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

module pbouda.jeffrey.profile.persistence.api {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.shared.persistence;
    requires transitive pbouda.jeffrey.jfrparser.api;
    requires transitive org.eclipse.collections.api;
    requires transitive java.sql;
    requires jakarta.validation;

    exports pbouda.jeffrey.provider.profile;
    exports pbouda.jeffrey.provider.profile.builder;
    exports pbouda.jeffrey.provider.profile.cache;
    exports pbouda.jeffrey.provider.profile.model;
    exports pbouda.jeffrey.provider.profile.model.recording;
    exports pbouda.jeffrey.provider.profile.model.writer;
    exports pbouda.jeffrey.provider.profile.repository;
}
