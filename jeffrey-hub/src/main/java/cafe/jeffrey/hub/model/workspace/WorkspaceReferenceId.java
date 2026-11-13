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

package cafe.jeffrey.hub.model.workspace;

import java.util.regex.Pattern;

/**
 * Whether a directory at the volume's root names a workspace: 3-64 characters, alphanumeric and
 * dashes, no leading or trailing dash, optionally prefixed by {@code $} for a system workspace.
 * Any other directory there is not one the hub visits.
 */
public final class WorkspaceReferenceId {

    private static final Pattern PATTERN = Pattern.compile(
            "^(\\$[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9])$");

    private WorkspaceReferenceId() {
    }

    public static boolean isValid(String referenceId) {
        return referenceId != null && PATTERN.matcher(referenceId).matches();
    }
}
