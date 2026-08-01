/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.advisor.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;

import java.util.List;
import java.util.Optional;

/**
 * Finds the commit a recording's build came from, so the advisor can tell the user whether the source
 * folder on disk is the source that actually ran.
 *
 * <p>Against a remote clone this decided <em>what to check out</em>. Against a local working copy it
 * decides <em>what to warn about</em>: Jeffrey cannot move somebody's working tree, but it can say
 * "this profile came from a different commit than the one you have checked out" instead of letting the
 * model reason confidently about code that never executed.</p>
 *
 * <p>When no tag is present the answer is empty rather than a guess, and the UI reports the commit as
 * unknown rather than quietly implying the source matched.</p>
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

        LOG.debug("No commit tag on recording; the source folder's commit cannot be compared: recording_id={}",
                recordingId);
        return Optional.empty();
    }
}
