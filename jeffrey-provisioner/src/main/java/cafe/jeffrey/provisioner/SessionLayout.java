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

package cafe.jeffrey.provisioner;

import java.nio.file.Path;

/**
 * Where a provisioner run put things. Everything downstream — the placeholder source, the
 * {@code .env} file, the argfile — needs some subset of these paths, and each used to restate its
 * own subset as a separate record built from the same locals.
 *
 * @param jeffreyHome null when the run was configured with an explicit workspaces directory
 *                    instead, in which case there is no Jeffrey home to speak of
 */
public record SessionLayout(
        Path jeffreyHome,
        Path workspaces,
        Path workspace,
        Path project,
        Path session) {

    public SessionLayout {
        if (workspaces == null || workspace == null || project == null || session == null) {
            throw new IllegalArgumentException("Only jeffreyHome may be absent from a session layout");
        }
    }

    /** Whether this run laid its directories out under a Jeffrey home. */
    public boolean hasJeffreyHome() {
        return jeffreyHome != null;
    }

    /** The file the profiler writes recordings to, by the session's own naming template. */
    public Path recordingFilePattern(String template) {
        return session.resolve(template);
    }
}
