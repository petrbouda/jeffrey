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

package cafe.jeffrey.hub.core.project.session;

/**
 * The outcome of looking into a session directory for anything the session recorded.
 *
 * <p>Three states for the same reason {@link LivenessRead} has them. <b>Empty</b> is evidence:
 * the directory was listed and holds nothing but the hidden entries the provisioner and the
 * agent leave there, so no profiler ever opened a file in it. <b>Unreadable</b> is the absence of
 * evidence: the directory is missing, or this hub could not list it. {@link SessionFinisher}
 * finishes a silent session on the first and never on the second — finishing is irreversible,
 * and a volume that answers an {@code IOException} would otherwise end every session on it.</p>
 */
public sealed interface SessionContentRead {

    /** The directory holds at least one visible entry: something in the session wrote a file. */
    record HoldsData() implements SessionContentRead {

        private static final HoldsData INSTANCE = new HoldsData();
    }

    /** The directory was listed and holds no visible entry. */
    record Empty() implements SessionContentRead {

        private static final Empty INSTANCE = new Empty();
    }

    /**
     * The directory is missing or could not be listed.
     *
     * @param reason what went wrong, for the log line that says why a session was left alone
     */
    record Unreadable(String reason) implements SessionContentRead {
    }

    static SessionContentRead holdsData() {
        return HoldsData.INSTANCE;
    }

    static SessionContentRead empty() {
        return Empty.INSTANCE;
    }

    static SessionContentRead unreadable(String reason) {
        return new Unreadable(reason);
    }
}
