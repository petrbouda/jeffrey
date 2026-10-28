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

import cafe.jeffrey.shared.common.JeffreyLayout;

import java.nio.file.Path;
import java.time.ZoneOffset;

/**
 * Where a provisioner run put things. Everything downstream — the placeholder source, the
 * {@code .env} file, the argfile — needs some subset of these paths, and each used to restate its
 * own subset as a separate record built from the same locals.
 */
public record SessionLayout(
        Path jeffreyHome,
        Path workspaces,
        Path workspace,
        Path project,
        Path session,
        ZoneOffset offset) {

    public SessionLayout {
        if (jeffreyHome == null || workspaces == null || workspace == null || project == null || session == null
                || offset == null) {
            throw new IllegalArgumentException("A session layout needs every directory down to the session and its UTC offset");
        }
    }

    /**
     * The file the profiler writes recordings to, by the session's own naming template with the
     * machine's UTC offset in it: async-profiler writes {@code %t} in local time, and the offset
     * after it is what lets every reader turn the name back into an instant.
     */
    public Path recordingFilePattern(String template) {
        return session.resolve(JeffreyLayout.recordingTemplate(template, offset));
    }
}
