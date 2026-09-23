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

/**
 * How much of a session a listing loads. {@link #HEADERS} is the rows — enough to pick a
 * session by id, status or age. {@link #WITH_FILES} walks the session's directory on the
 * volume as well, which is a stat per file over a network mount: what
 * {@code openRecording()}, {@code finishedRecordings()}, {@code totalSizeBytes()} and
 * {@code isFailedEmpty()} answer from, and meaningless without.
 */
public enum SessionDetail {
    HEADERS,
    WITH_FILES;

    public boolean withFiles() {
        return this == WITH_FILES;
    }
}
