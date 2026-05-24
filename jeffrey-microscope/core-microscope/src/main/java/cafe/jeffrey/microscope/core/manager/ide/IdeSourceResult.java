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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Outcome of an IDE source-fetch attempt. A {@code false} success with a human-readable message
 * represents an expected, non-fatal condition (e.g. the IDE plugin is offline or has no source for
 * the class), not a server error. On success {@code content} holds the raw source text.
 */
public record IdeSourceResult(boolean success, String content, String message) {

    public static IdeSourceResult succeeded(String content) {
        return new IdeSourceResult(true, content, null);
    }

    public static IdeSourceResult failed(String message) {
        return new IdeSourceResult(false, null, message);
    }
}
