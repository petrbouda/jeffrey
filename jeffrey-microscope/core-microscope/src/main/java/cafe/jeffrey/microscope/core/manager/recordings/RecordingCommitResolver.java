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

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Finds the commit a recording's build came from, so a reader holding a checkout can tell whether the
 * source in front of them is the source that actually ran.
 *
 * <p>The answer travels out through the external MCP server: a coding agent sitting in the repository
 * compares it with its own {@code HEAD} before mapping hot frames to code, instead of reasoning
 * confidently about code that never executed.</p>
 *
 * <p>When no tag is present the answer is empty rather than a guess, and the caller reports the commit
 * as unknown rather than quietly implying the source matched.</p>
 */
public class RecordingCommitResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingCommitResolver.class);

    // Checked in order. The first is what Jeffrey's own tooling writes; the rest are the conventional
    // names CI systems and buildpacks already use, so an existing pipeline often needs no change.
    private static final List<String> COMMIT_TAG_KEYS = List.of(
            "git.commit",
            "git.commit.id",
            "git_commit",
            "vcs.revision",
            "org.opencontainers.image.revision");

    private final RecordingTagsRepository recordingTagsRepository;

    public RecordingCommitResolver(RecordingTagsRepository recordingTagsRepository) {
        this.recordingTagsRepository = recordingTagsRepository;
    }

    public Optional<String> resolve(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return Optional.empty();
        }

        List<RecordingTag> tags = recordingTagsRepository.listForRecording(recordingId);
        for (String key : COMMIT_TAG_KEYS) {
            Optional<String> value = tags.stream()
                    .filter(tag -> key.equalsIgnoreCase(tag.key()))
                    .map(RecordingTag::value)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst();
            if (value.isPresent()) {
                LOG.debug("Resolved commit for recording: recording_id={} tag_key={} commit_ref={}",
                        recordingId, key, value.get());
                return value;
            }
        }

        LOG.debug("No commit tag on recording; the checkout cannot be compared: recording_id={}", recordingId);
        return Optional.empty();
    }
}
