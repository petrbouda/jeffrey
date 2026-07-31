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

package cafe.jeffrey.performance.analyst.recommendations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;

import java.util.List;
import java.util.Optional;

/**
 * Finds the commit a recording's build came from, so the analysis reads the source that actually ran.
 *
 * <p>Without this, the model reads whatever is on the default branch right now. If production is
 * several merges behind, it will confidently reason about code that never executed — and it has no way
 * to notice. The revision is read from the recording's tags, which the Provisioner, the agent or a CI
 * job can stamp at the point where the build identity is still known.</p>
 *
 * <p>When no tag is present the answer is empty rather than a guess. The caller surfaces that as
 * "commit unknown" instead of quietly implying the source matched.</p>
 */
public class CommitRefResolver {

    private static final Logger LOG = LoggerFactory.getLogger(CommitRefResolver.class);

    // Checked in order. The first is what Jeffrey's own tooling writes; the rest are the conventional
    // names CI systems and buildpacks already use, so an existing pipeline often needs no change.
    private static final List<String> COMMIT_TAG_KEYS = List.of(
            "git.commit",
            "git.commit.id",
            "git_commit",
            "vcs.revision",
            "org.opencontainers.image.revision");

    private final RecordingTagsRepository recordingTagsRepository;

    public CommitRefResolver(RecordingTagsRepository recordingTagsRepository) {
        this.recordingTagsRepository = recordingTagsRepository;
    }

    public Optional<String> resolve(String recordingId) {
        List<RecordingTag> tags = recordingTagsRepository.listForRecording(recordingId);

        for (String key : COMMIT_TAG_KEYS) {
            Optional<String> value = tags.stream()
                    .filter(tag -> key.equalsIgnoreCase(tag.key()))
                    .map(RecordingTag::value)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst();

            if (value.isPresent()) {
                LOG.info("Resolved commit for recording: recording_id={} tag_key={} commit_ref={}",
                        recordingId, key, value.get());
                return value;
            }
        }

        LOG.info("No commit tag on recording; analysis will read the default branch: recording_id={}", recordingId);
        return Optional.empty();
    }
}
