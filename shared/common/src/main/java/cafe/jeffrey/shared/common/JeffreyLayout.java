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

package cafe.jeffrey.shared.common;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * The on-disk layout contract of the shared workspaces filesystem.
 *
 * <p>The provisioner (writer) creates this structure on the shared volume, and every reader takes
 * it from the directory names alone: a level is the listing of its parent, and an id is a
 * directory name. No file on the volume is a marker:</p>
 *
 * <pre>
 * &lt;workspaces&gt;/
 *   &lt;workspace-ref-id&gt;/          the workspace's id and its name
 *     &lt;project-name&gt;/
 *       &lt;instance-name&gt;/
 *         &lt;session-uuidv7&gt;/        the session's start is in its name
 *           rec-&lt;local time&gt;&lt;UTC offset&gt;.jfr
 * </pre>
 *
 * <p>async-profiler writes {@code %t} in the machine's local time, so the provisioner writes the
 * machine's UTC offset into each recording's name ({@link #recordingTemplate}), and a reader turns
 * the name into an instant from the name alone ({@link #recordingStart}).</p>
 *
 * <p>Both sides must resolve these names from this single class; any rename
 * silently desynchronizes producer and consumer otherwise.</p>
 */
public abstract class JeffreyLayout {

    /** Directory under the Jeffrey home that holds all workspaces */
    public static final String WORKSPACES_DIR = "workspaces";

    /** async-profiler's placeholder for the time a recording's file was opened. */
    public static final String TIMESTAMP_PLACEHOLDER = "%t";

    /**
     * What a recording's name starts with — {@code rec}, not {@code profile}: in Jeffrey a profile
     * is what Microscope builds from a recording.
     */
    private static final String RECORDING_PREFIX = "rec-";

    private static final String RECORDING_EXTENSION = ".jfr";

    /** async-profiler's {@code %t}: the local time a recording's file was opened. */
    public static final String TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss";

    /**
     * How a recording's name writes its UTC offset, right after the timestamp: {@code +0100},
     * {@code -0330}, {@code +0000} — no character a file share refuses in a name.
     */
    private static final String OFFSET_PATTERN = "+HHMM";
    private static final String UTC_OFFSET_TEXT = "+0000";

    private static final DateTimeFormatter OFFSET_FORMATTER = new DateTimeFormatterBuilder()
            .appendOffset(OFFSET_PATTERN, UTC_OFFSET_TEXT)
            .toFormatter();

    /** A recording's timestamp, then its offset when the name carries one; UTC when it does not. */
    private static final DateTimeFormatter START_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern(TIMESTAMP_PATTERN)
            .optionalStart()
            .appendOffset(OFFSET_PATTERN, UTC_OFFSET_TEXT)
            .optionalEnd()
            .parseDefaulting(ChronoField.OFFSET_SECONDS, ZoneOffset.UTC.getTotalSeconds())
            .toFormatter();

    /**
     * The provisioner's recording file name, with the machine's UTC offset in it: {@code rec-%t+0100.jfr},
     * {@code rec-20261027-085500+0100.jfr} once async-profiler fills in {@code %t}. The offset is
     * the one in effect when the session starts: a JVM that runs through a daylight-saving change
     * keeps writing local time, so its later chunks read an hour off (owner decision).
     */
    public static String recordingTemplate(ZoneOffset offset) {
        return RECORDING_PREFIX + TIMESTAMP_PLACEHOLDER + OFFSET_FORMATTER.format(offset) + RECORDING_EXTENSION;
    }

    /**
     * When a recording started, read off its id — its name without the extension:
     * {@code rec-20261027-085500+0100}, or without an offset, read as UTC. Empty for a name
     * outside the convention, a chunk an older provisioner named {@code profile-…} included.
     */
    public static Optional<Instant> recordingStart(String id) {
        if (!id.startsWith(RECORDING_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(START_FORMATTER.parse(id.substring(RECORDING_PREFIX.length()), Instant::from));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
