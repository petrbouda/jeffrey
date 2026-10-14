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

package cafe.jeffrey.hub.core.project.repository;

import java.nio.file.Path;

/**
 * The outcome of looking up one file of a session for download: the path to serve, or why the
 * hub will not serve it. Each refusal carries its own sentence, since a caller that asked for one
 * file needs to know which of these happened.
 */
public sealed interface FileLookup {

    /**
     * The file to serve, under the name it has now.
     *
     * @param path where the file lies; its name may differ from the listed one when the
     *             compression job has replaced a recording with its archive
     */
    record Found(Path path) implements FileLookup {
    }

    /** The hub will not hand the file over; {@link #reason()} says why. */
    sealed interface Refused extends FileLookup {

        String reason();
    }

    /** The session holds no file with that id. */
    record NotInSession(String sessionId, String fileId) implements Refused {

        @Override
        public String reason() {
            return "Session " + sessionId + " holds no file with id " + fileId
                    + ". Take the id from the session's file listing.";
        }
    }

    /** The chunk the profiler is still writing; reading it would give a truncated answer. */
    record StillWriting(String fileName) implements Refused {

        @Override
        public String reason() {
            return "File " + fileName + " is the chunk the profiler is still writing, and reading it "
                    + "would give a truncated answer. It can be taken once the profiler has rolled the next one.";
        }
    }

    /** A recording the profiler stopped before writing an event into. Empty artifacts are served. */
    record EmptyRecording(String fileName) implements Refused {

        @Override
        public String reason() {
            return "Recording " + fileName + " is empty — the profiler stopped before it wrote an event into it.";
        }
    }

    /**
     * Listed, but gone by the time it was reached: the compression job replaced the recording with
     * its archive. The id survives that, so asking for the same id again names the archive.
     */
    record Vanished(String sessionId, String fileName) implements Refused {

        @Override
        public String reason() {
            return "File " + fileName + " was listed for session " + sessionId + " but is no longer on disk. "
                    + "It may have been replaced by its compressed form; ask for the same file id again.";
        }
    }
}
