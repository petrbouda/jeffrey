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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.model.Type;

/**
 * Scopes an I/O event stream to a single endpoint — a socket peer ({@code host:port}) or a file
 * path — or lets every event through. Used to plot one peer's throughput next to the aggregate.
 *
 * @param target the endpoint to keep, or {@code null} to keep everything
 */
public record IoTargetFilter(String target) {

    private static final IoTargetFilter ALL = new IoTargetFilter(null);

    /**
     * Keeps every event of the stream.
     */
    public static IoTargetFilter all() {
        return ALL;
    }

    /**
     * Keeps only the events of the given endpoint; a blank or {@code null} target keeps everything,
     * so an omitted request parameter degrades to the aggregate view.
     */
    public static IoTargetFilter ofNullable(String target) {
        return target == null || target.isBlank() ? ALL : new IoTargetFilter(target);
    }

    boolean matches(Type type, ObjectNode fields) {
        return target == null || target.equals(IoEventFields.target(type, fields));
    }
}
