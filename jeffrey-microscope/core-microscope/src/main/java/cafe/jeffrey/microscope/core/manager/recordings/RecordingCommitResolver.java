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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
 *
 * <p>A coding agent that records and imports a build itself knows its commit and passes it to
 * {@code recordings_analyzeFile}, which writes it here with {@link #record}; a recording made anywhere
 * else is identified by the {@code -Dgit.commit=…} system property its JVM ran with, which
 * {@link #revision} reads when no tag says otherwise.</p>
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

    /** The tag a recording's working-tree state is written under, and read from. */
    static final String DIRTY_TAG = "git.dirty";

    /** The tag a recording's branch is read from. */
    static final String BRANCH_TAG = "git.branch";

    /** The commit tag {@link #record} writes. */
    private static final String COMMIT_TAG = "git.commit";

    /** System properties a build's revision travels in, checked in order; the same names as the tags. */
    private static final List<String> COMMIT_PROPERTIES = List.of("git.commit", "git.commit.id", "vcs.revision");
    private static final String DIRTY_PROPERTY = "git.dirty";
    private static final String BRANCH_PROPERTY = "git.branch";

    private final RecordingTagsRepository recordingTagsRepository;

    public RecordingCommitResolver(RecordingTagsRepository recordingTagsRepository) {
        this.recordingTagsRepository = recordingTagsRepository;
    }

    /**
     * Writes what an import was told about the build: its commit, and whether its working tree was dirty.
     *
     * @param dirty null when the caller did not say
     */
    public void record(String recordingId, String commit, Boolean dirty) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(COMMIT_TAG, SourceRevision.requireCommit(commit));
        if (dirty != null) {
            tags.put(DIRTY_TAG, dirty.toString());
        }
        recordingTagsRepository.insert(recordingId, tags);
        LOG.info("Recorded the source revision of a recording: recording_id={} commit={} dirty={}",
                recordingId, tags.get(COMMIT_TAG), dirty);
    }

    /**
     * The revision a recording's build came from: its tags first, then the system properties its JVM ran
     * with, read only when no tag names a commit.
     *
     * @param systemProperties the profiled JVM's initial system properties, by name
     */
    public Optional<SourceRevision> revision(String recordingId, Supplier<Map<String, String>> systemProperties) {
        Optional<String> tagged = resolve(recordingId).filter(SourceRevision::isCommit);
        if (tagged.isPresent()) {
            List<RecordingTag> tags = recordingTagsRepository.listForRecording(recordingId);
            return Optional.of(new SourceRevision(tagged.get(), SourceRevision.Source.TAG,
                    flag(tagValue(tags, DIRTY_TAG)), tagValue(tags, BRANCH_TAG)));
        }
        Map<String, String> properties = systemProperties.get();
        for (String key : COMMIT_PROPERTIES) {
            String value = properties.get(key);
            if (SourceRevision.isCommit(value)) {
                return Optional.of(new SourceRevision(value, SourceRevision.Source.SYSTEM_PROPERTY,
                        flag(properties.get(DIRTY_PROPERTY)), properties.get(BRANCH_PROPERTY)));
            }
        }
        return Optional.empty();
    }

    private static String tagValue(List<RecordingTag> tags, String key) {
        return tags.stream()
                .filter(tag -> key.equalsIgnoreCase(tag.key()))
                .map(RecordingTag::value)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** {@code true}/{@code false} as written, anything else as unknown. */
    private static Boolean flag(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (Boolean.TRUE.toString().equalsIgnoreCase(trimmed)) {
            return Boolean.TRUE;
        }
        if (Boolean.FALSE.toString().equalsIgnoreCase(trimmed)) {
            return Boolean.FALSE;
        }
        return null;
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
