/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /**
     * One open project window. {@code hasClass} marks windows that contain the requested frame, and
     * {@code vcsBranch} / {@code headCommit} say which checkout it is sitting on — the pair a caller
     * compares against the commit a recording was tagged with before trusting a file and a line.
     */
    public record IdeProjectView(
            String id,
            String name,
            String basePath,
            String vcsBranch,
            String headCommit,
            boolean focused,
            boolean hasClass
    ) {
    }
}
