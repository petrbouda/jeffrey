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

package cafe.jeffrey.recordings.core;

import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The local recording an identical download already made, so a repeat hands it back instead of
 * storing the same chunks again - and, analysed once more, a second profile of them that the
 * catalogue lists beside the first with the same start and duration.
 * <p>
 * Identical means: downloaded from the same remote session, by the same {@code origin.*} identity
 * tags the download would write, as a part or as the whole alike, and holding exactly the recording
 * chunks about to be fetched. Their identity excludes the JFR or JFR/LZ4 suffix: compression changes
 * a chunk's representation, not the recording it holds. A chunk's name carries its start, and the
 * profiler writes a file whole, so the same chunk identities on the same session are the same data.
 * A session that grew has a new name in the set, and is fetched. A whole copy of a
 * session taken while it was its instance's newest lacks {@link OriginContext#TAG_COMPLETE}, so once a
 * newer session has ended it, the whole download is made again and the copy that stands in for the
 * session is the complete one.
 */
final class SameDownload {

    private static final String EXTENSION_SEPARATOR = ".";
    private static final Set<ManagedFile> CHUNK_TYPES = Set.of(ManagedFile.JFR, ManagedFile.JFR_LZ4);

    /** The tags that say where a copy came from and what it stands for; display names are left out. */
    private static final Set<String> IDENTITY_TAGS = Set.of(
            OriginContext.TAG_REMOTE_ADDRESS,
            OriginContext.TAG_WORKSPACE_ID,
            OriginContext.TAG_PROJECT_ID,
            OriginContext.TAG_RECORDING_ID,
            OriginContext.TAG_INSTANCE_ID,
            OriginContext.TAG_COMPLETE);

    private SameDownload() {
    }

    /**
     * @param recordings     the local store
     * @param originTags     the tags the download would write on the recording it makes
     * @param recordingFiles the names of the recording files it would fetch
     * @return the newest local recording that is the same download, if any
     */
    static Optional<Recording> find(
            RecordingsCoreManager recordings, Map<String, String> originTags, Set<String> recordingFiles) {

        if (!originTags.containsKey(OriginContext.TAG_REMOTE_ADDRESS)
                || !originTags.containsKey(OriginContext.TAG_RECORDING_ID)) {
            return Optional.empty();
        }
        Set<String> wantedChunks = chunkIds(recordingFiles);
        List<Recording> local = recordings.listRecordings().stream()
                .filter(recording -> wantedChunks.equals(chunkIds(fileNames(recording))))
                .toList();
        if (local.isEmpty()) {
            return Optional.empty();
        }
        Map<String, List<RecordingTag>> tags = recordings.tagsForRecordings(local.stream().map(Recording::id).toList());
        Map<String, String> wanted = identity(originTags);
        boolean part = originTags.containsKey(OriginContext.TAG_WINDOW);
        return local.stream()
                .filter(recording -> {
                    Map<String, String> held = byKey(tags.get(recording.id()));
                    return wanted.equals(identity(held)) && part == held.containsKey(OriginContext.TAG_WINDOW);
                })
                .max(Comparator.comparing(Recording::createdAt, Comparator.nullsFirst(Comparator.<Instant>naturalOrder())));
    }

    private static Set<String> chunkIds(Set<String> names) {
        return names.stream().map(name -> {
            ManagedFile type = ManagedFile.of(name);
            return CHUNK_TYPES.contains(type)
                    ? name.substring(0, name.length() - type.fileExtension().length() - EXTENSION_SEPARATOR.length())
                    : name;
        }).collect(Collectors.toSet());
    }

    /** An existing chunk set can still need a missing dump or a log that has grown. */
    static List<RepositoryFile> missingArtifacts(
            RecordingsCoreManager recordings, Recording existing, List<RepositoryFile> requested) {
        if (requested.isEmpty() || !existing.inBundle()) {
            return requested;
        }
        Map<String, Long> held = new HashMap<>();
        recordings.findBundle(existing.bundleId()).ifPresent(bundle -> bundle.files()
                .forEach(file -> held.put(file.filename(), file.sizeInBytes())));
        recordings.findBundleMembers(existing.bundleId()).forEach(member -> member.files()
                .forEach(file -> held.put(file.filename(), file.sizeInBytes())));
        return requested.stream()
                .filter(file -> file.size() <= 0 || !Long.valueOf(file.size()).equals(held.get(file.name())))
                .toList();
    }

    private static Set<String> fileNames(Recording recording) {
        return recording.files() == null
                ? Set.of()
                : recording.files().stream().map(RecordingFile::filename).collect(Collectors.toSet());
    }

    private static Map<String, String> byKey(List<RecordingTag> tags) {
        Map<String, String> byKey = new HashMap<>();
        if (tags != null) {
            for (RecordingTag tag : tags) {
                byKey.put(tag.key(), tag.value());
            }
        }
        return byKey;
    }

    private static Map<String, String> identity(Map<String, String> tags) {
        Map<String, String> identity = new HashMap<>(tags);
        identity.keySet().retainAll(IDENTITY_TAGS);
        return identity;
    }
}
