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

import java.util.List;

/**
 * Aggregated discovery result that backs the frontend's IDE-target picker (grouped by instance).
 * {@code selectedProjectId} is the currently cached choice for the profile (nullable), so the UI can
 * pre-select it and skip the picker when it is still present.
 */
public record IdeTargetsResult(String selectedProjectId, List<IdeInstanceView> instances) {

    public static IdeTargetsResult empty() {
        return new IdeTargetsResult(null, List.of());
    }

    /** One discovered IDE instance (one process / one built-in-server port). */
    public record IdeInstanceView(
            int port,
            String ideName,
            String ideVersion,
            long pid,
            List<IdeProjectView> projects
    ) {
    }

    /** One open project window. {@code hasClass} marks windows that contain the requested frame. */
    public record IdeProjectView(
            String id,
            String name,
            String basePath,
            String vcsBranch,
            boolean focused,
            boolean hasClass
    ) {
    }
}
