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

module pbouda.jeffrey.profile.guardian {
    requires transitive pbouda.jeffrey.frameir;
    requires transitive tools.jackson.databind;
    requires transitive tools.jackson.core;
    requires spring.boot;
    requires org.slf4j;

    exports pbouda.jeffrey.profile.guardian;
    exports pbouda.jeffrey.profile.guardian.guard;
    exports pbouda.jeffrey.profile.guardian.guard.alloc;
    exports pbouda.jeffrey.profile.guardian.guard.app;
    exports pbouda.jeffrey.profile.guardian.guard.blocking;
    exports pbouda.jeffrey.profile.guardian.guard.gc;
    exports pbouda.jeffrey.profile.guardian.guard.jit;
    exports pbouda.jeffrey.profile.guardian.guard.jvm;
    exports pbouda.jeffrey.profile.guardian.matcher;
    exports pbouda.jeffrey.profile.guardian.metadata;
    exports pbouda.jeffrey.profile.guardian.preconditions;
    exports pbouda.jeffrey.profile.guardian.prereq;
    exports pbouda.jeffrey.profile.guardian.traverse;
    exports pbouda.jeffrey.profile.guardian.type;
}
