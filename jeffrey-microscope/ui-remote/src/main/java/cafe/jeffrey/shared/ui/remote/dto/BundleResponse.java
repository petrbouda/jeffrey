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

package cafe.jeffrey.shared.ui.remote.dto;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.storage.recording.api.file.Bundle;

import java.util.List;

/**
 * A bundle as the recordings list shows it: the row its members are listed under. The members
 * themselves are ordinary {@link RecordingResponse}s carrying the bundle's id.
 *
 * @param files the bundle's download-only files (logs, crash files)
 * @param tags  where the bundle came from, as every member carries it
 */
public record BundleResponse(
        String id,
        String name,
        String groupId,
        long createdAt,
        List<RecordingFileResponse> files,
        List<RecordingTagResponse> tags) {

    public static BundleResponse from(Bundle bundle, List<RecordingTag> tags) {
        List<RecordingFileResponse> files = bundle.files().stream()
                .map(file -> new RecordingFileResponse(
                        file.id(),
                        file.filename(),
                        file.sizeInBytes(),
                        file.fileType().name(),
                        file.fileType().description()))
                .toList();

        return new BundleResponse(
                bundle.id(),
                bundle.bundleName(),
                bundle.groupId(),
                bundle.createdAt().toEpochMilli(),
                files,
                tags.stream().map(RecordingTagResponse::from).toList());
    }
}
