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
import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *           rec-&lt;local time&gt;&lt;UTC offset&gt;_&lt;sequence&gt;.jfr
 * </pre>
 *
 * <p>async-profiler writes {@code %t} in the machine's local time, so the provisioner writes the
 * machine's UTC offset into each recording's name ({@link #recordingTemplate}), and a reader turns
 * the name into an instant from the name alone ({@link #recordingStart}). The offset is the one at
 * the session's start, and local time repeats an hour when daylight saving ends, so the name also
 * carries async-profiler's file number ({@link #recordingSequence}): without it the chunks of the
 * repeated hour took the names of the hour before. A session's chunks are ordered by that number
 * ({@link #recordingOrder}).</p>
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
     * async-profiler's placeholder for the file's number: 0 for the JVM's first file, one more at
     * each loop restart. Never reset or wrapped (no {@code {MAX}}), so a JVM never repeats a name.
     */
    public static final String SEQUENCE_PLACEHOLDER = "%n";

    /**
     * Between the offset and the number. Not {@code -}: an offset such as {@code -0330} would read
     * as a number in a name that has none.
     */
    public static final String SEQUENCE_SEPARATOR = "_";

    /** The number at the end of a recording's id, after its separator. */
    private static final Pattern SEQUENCE_SUFFIX = Pattern.compile(SEQUENCE_SEPARATOR + "(\\d+)$");

    /** Where a chunk without a number sorts among its session's chunks: before every numbered one. */
    private static final long NO_SEQUENCE = -1L;

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
     * The provisioner's recording file name, with the machine's UTC offset and the file's number in
     * it: {@code rec-%t+0100_%n.jfr}, {@code rec-20261027-085500+0100_0.jfr} once async-profiler
     * fills them in. The offset is the one in effect when the session starts: a JVM that runs
     * through a daylight-saving change keeps writing local time, so its later chunks read an hour
     * off (owner decision). The number keeps the names of the hour that repeats when the clocks go
     * back from colliding with the hour before.
     */
    public static String recordingTemplate(ZoneOffset offset) {
        return RECORDING_PREFIX + TIMESTAMP_PLACEHOLDER + OFFSET_FORMATTER.format(offset)
                + SEQUENCE_SEPARATOR + SEQUENCE_PLACEHOLDER + RECORDING_EXTENSION;
    }

    /**
     * When a recording started, read off its id — its name without the extension:
     * {@code rec-20261027-085500+0100_7}, or without the number, or without an offset, read as UTC.
     * Empty for a name outside the convention, a chunk an older provisioner named {@code profile-…}
     * included.
     */
    public static Optional<Instant> recordingStart(String id) {
        if (!id.startsWith(RECORDING_PREFIX)) {
            return Optional.empty();
        }
        String stamp = SEQUENCE_SUFFIX.matcher(id.substring(RECORDING_PREFIX.length())).replaceFirst("");
        try {
            return Optional.of(START_FORMATTER.parse(stamp, Instant::from));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * The file's number async-profiler wrote into a recording's id ({@code rec-…+0100_7} → 7), or
     * empty for a name without one: one written before the number was added, or by a custom
     * profiler command that names its own files.
     */
    public static OptionalLong recordingSequence(String id) {
        if (!id.startsWith(RECORDING_PREFIX)) {
            return OptionalLong.empty();
        }
        Matcher matcher = SEQUENCE_SUFFIX.matcher(id);
        if (!matcher.find()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * The order of one session's recordings, oldest first — the one rule both sides order chunks
     * by. By the number in their names, which only grows: their timestamps go back an hour when
     * daylight saving ends, so ordered by time the chunk being written would not be the newest. A
     * chunk without a number sorts by its start, before the numbered ones; one session's chunks
     * are all of one kind, since its template does not change while it runs.
     *
     * @param id    a recording's id, its name without the extension
     * @param start when it started, read off that name
     */
    public static <T> Comparator<T> recordingOrder(Function<T, String> id, Function<T, Instant> start) {
        return Comparator.<T>comparingLong(recording -> recordingSequence(id.apply(recording)).orElse(NO_SEQUENCE))
                .thenComparing(start);
    }
}
