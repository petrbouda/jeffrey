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

package cafe.jeffrey.microscope.persistence.api;

/**
 * The IDE window a profile is linked to, as much of it as is worth keeping.
 *
 * <p>A link has a volatile half and a durable half. The port a window answered on and the pid it ran
 * under describe one run of one IDE process; they are wrong the moment either side restarts, which is
 * exactly when a stored link is read back. What survives is the choice the reader made — which
 * project, in which IDE — and discovery turns that back into a port when a jump next needs one.
 *
 * @param projectId   the IDE's stable identifier for the window ({@code Project.getLocationHash()}),
 *                    and what discovery matches on to find the window again
 * @param projectName the window's display name, kept so the UI can name the link without scanning
 * @param ideName     the IDE's product name, for the same reason
 * @param basePath    the project's directory on disk — the checkout this profile is about, and the
 *                    one an AI analysis may be allowed to read
 */
public record IdeTargetLink(
        String projectId,
        String projectName,
        String ideName,
        String basePath) {

    public IdeTargetLink {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
    }
}
