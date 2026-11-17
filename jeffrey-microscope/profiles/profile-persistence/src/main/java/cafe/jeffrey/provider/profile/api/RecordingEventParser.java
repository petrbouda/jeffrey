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

public interface RecordingEventParser {

    /**
     * Parses every recording file into {@code eventWriter}. The files are independent inputs, not
     * pieces of one that has to be put back together, so an implementation is free to read them
     * in whatever order and with whatever parallelism suits it.
     */
    void start(EventWriter eventWriter, RecordingSources sources);

}
