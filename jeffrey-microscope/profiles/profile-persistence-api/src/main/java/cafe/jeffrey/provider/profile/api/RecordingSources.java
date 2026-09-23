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

package cafe.jeffrey.provider.profile.api;

import java.nio.file.Path;
import java.util.List;

/**
 * The recording files one profile is built from, oldest first.
 * <p>
 * A hub session rolls a chunk every few minutes, so a profile is routinely made of many files.
 * They are never joined: each is a self-contained JFR, they are parsed independently into one
 * {@link EventWriter}, and the only thing that spans the set is the recording's window — the
 * earliest start and the latest end across their chunk headers. A single uploaded file is the
 * same shape with one element, which is what {@link #of(Path)} is for.
 * <p>
 * Order matters for nothing the parse does — events carry absolute timestamps and the writers
 * are independent — but it is kept so that whatever is reported about "the recording" reads the
 * same way twice.
 */
public record RecordingSources(List<Path> files) {

    public RecordingSources {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one recording file is required");
        }
        files = List.copyOf(files);
    }

    public static RecordingSources of(Path recording) {
        return new RecordingSources(List.of(recording));
    }

    /**
     * The first file, for the caller that needs a single representative — a display name, or the
     * one-file formats that cannot hold more than one anyway.
     */
    public Path first() {
        return files.getFirst();
    }

    public int size() {
        return files.size();
    }
}
