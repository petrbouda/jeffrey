/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.api;


public interface RecordingInformationParser {

    /**
     * Retrieves the recording information for a given recording.
     * <p>
     * Across several files the answer is one recording's worth: the earliest start, the latest
     * end, and the sizes added up. The window matters beyond display — it anchors the relative
     * timeline written with every event — so it has to span all of them.
     *
     * @param sources the files the recording is made of
     * @return the recording information
     */
    RecordingInformation provide(RecordingSources sources);
}
