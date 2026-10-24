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

package cafe.jeffrey.storage.recording.api.file;

import java.time.Instant;
import java.util.List;

/**
 * Several files that arrived together — a hub session download — kept as one row in the recordings
 * list. A bundle is never initialized itself: each initializable item in it is a {@link Recording} of
 * its own (all JFR chunks as one, each heap dump, the perf counters), pointing back here through
 * {@link Recording#bundleId()}. What cannot be initialized — logs, crash files — stays here as
 * download-only {@link BundleFile}s.
 *
 * @param groupId the user's folder the bundle sits in; its member recordings carry none of their own
 * @param files   the bundle's own download-only files
 */
public record Bundle(
        String id,
        String bundleName,
        String groupId,
        Instant createdAt,
        List<BundleFile> files) {

    public Bundle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Bundle id must not be blank");
        }
        files = files == null ? List.of() : List.copyOf(files);
    }

    public Bundle withFiles(List<BundleFile> files) {
        return new Bundle(id, bundleName, groupId, createdAt, files);
    }
}
