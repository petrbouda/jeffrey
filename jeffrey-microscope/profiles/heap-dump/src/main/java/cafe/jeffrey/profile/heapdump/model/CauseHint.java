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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Diagnostic annotation explaining why a class loader is being held alive.
 * Hints are derived heuristically by walking the GC root path and matching well-known
 * leak patterns (ThreadLocal, JDBC driver registration, JNI globals, etc.).
 *
 * @param kind        the kind of leak pattern matched
 * @param description short human-readable description (e.g. "Thread.threadLocals")
 * @param objectId    the object id of the matched step in the GC root path, or {@code -1}
 *                    if the hint applies to the path overall (e.g. JNI root type)
 */
public record CauseHint(
        HintKind kind,
        String description,
        long objectId
) {
}
