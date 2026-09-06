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

import java.time.Instant;

/**
 * Request to locate a source position in the developer's IDE without opening it.
 *
 * <p>The same question {@link IdeOpenRequest} asks, minus the jump. It exists for the callers that
 * only want to know where a frame lives — an MCP client grounding a finding in real files, or a
 * check made before offering a jump — because moving somebody's editor is a side effect they did not
 * ask for and cannot undo.
 *
 * @param profileId     the profile this lookup belongs to; selects the cached IDE window. Nullable
 * @param fqn           fully-qualified class name (e.g. {@code com.example.OrderService})
 * @param method        the method name; may be null when only the class is being located
 * @param line          source line number from the profile, or {@code -1} when unknown
 * @param recordingTime when the profile was recorded, used by the IDE to report whether the file has
 *                      been edited since. Null when unknown, and then nothing is reported as stale
 */
public record IdeResolveRequest(
        String profileId, String fqn, String method, int line, Instant recordingTime) {

    public IdeResolveRequest {
        if (fqn == null || fqn.isBlank()) {
            throw new IllegalArgumentException("fqn must not be blank");
        }
    }
}
