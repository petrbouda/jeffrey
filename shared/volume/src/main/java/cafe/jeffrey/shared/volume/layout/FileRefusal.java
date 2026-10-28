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

package cafe.jeffrey.shared.volume.layout;

/**
 * Why one file of a session is not handed over, in the words every reader of the volume uses —
 * the hub serving it, or Microscope reading the volume directly — so an agent gets the same
 * sentence whichever way the file was asked for.
 */
public enum FileRefusal {

    /** The session holds no file with that id; arguments: session id, file id. */
    NOT_IN_SESSION("Session %s holds no file with id %s. Take the id from the session's file listing."),

    /** A recording the profiler stopped before writing an event into; argument: file name. */
    EMPTY_RECORDING("Recording %s is empty — the profiler stopped before it wrote an event into it."),

    /**
     * Listed, but gone by the time it was reached — the compression job replaced the recording
     * with its archive, whose id is the same; arguments: file name, session id.
     */
    VANISHED("File %s was listed for session %s but is no longer on the volume. "
            + "It may have been replaced by its compressed form; ask for the same file id again.");

    private final String template;

    FileRefusal(String template) {
        this.template = template;
    }

    public String reason(Object... arguments) {
        return template.formatted(arguments);
    }
}
