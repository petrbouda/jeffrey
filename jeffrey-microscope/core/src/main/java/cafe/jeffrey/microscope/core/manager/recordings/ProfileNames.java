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

package cafe.jeffrey.microscope.core.manager.recordings;

import java.util.Set;

/**
 * The name a recording's next profile is listed under. The first is named after the recording, as a
 * profile always was; a later one is numbered ({@code checkout.jfr #2}), so the Profiles column tells
 * the initializations of one recording apart. The number skips every name a profile of the recording
 * already holds, so deleting one and initializing again never yields two profiles of the same name.
 */
final class ProfileNames {

    private static final String NUMBER_SEPARATOR = " #";
    private static final int FIRST_NUMBER = 2;

    private ProfileNames() {
    }

    /**
     * @param recordingName the recording's own name
     * @param takenNames    the names the recording's existing profiles are listed under
     */
    static String next(String recordingName, Set<String> takenNames) {
        if (takenNames.isEmpty()) {
            return recordingName;
        }
        int number = FIRST_NUMBER;
        while (takenNames.contains(recordingName + NUMBER_SEPARATOR + number)) {
            number++;
        }
        return recordingName + NUMBER_SEPARATOR + number;
    }
}
