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

package cafe.jeffrey.microscope.core.web.controllers.profile;

/**
 * Media types shared by the profile controllers.
 * <p>
 * Here rather than on whichever controller happened to declare one first: four controllers produce
 * protobuf, and three of them were importing the constant from the fourth, which reads as a
 * dependency between endpoints that have nothing to do with each other.
 */
public final class ProfileMediaTypes {

    /**
     * Flamegraphs are sent as protobuf rather than JSON: a graph is a deep tree of small nodes, and
     * the binary encoding is what keeps a large one inside a single response.
     */
    public static final String PROTOBUF = "application/x-protobuf";

    private ProfileMediaTypes() {
    }
}
