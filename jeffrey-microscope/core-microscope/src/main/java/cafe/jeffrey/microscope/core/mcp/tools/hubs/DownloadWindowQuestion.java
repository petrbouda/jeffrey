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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.core.mcp.tools.ByteSizes;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The question {@code hubs_download} puts to the user before it moves a large hub session across the
 * network: which part of it to bring — the last hour, the last few minutes, all of it, or a window
 * of their own.
 * <p>
 * Asked only about a session that runs longer than {@link #askOverDuration} or holds more than
 * {@link #askOverBytes}; anything smaller is downloaded whole, as it always was. Every window is
 * measured back from the session's end — when it finished, or now while it is still recording — and
 * is worked out afresh from the session as read on each call, so a retry answers about the session
 * as it stands then.
 * <p>
 * The form is flat and primitive, as the elicitation specification requires: one titled single
 * choice, one bounded integer and two {@code date-time} strings, all with defaults.
 *
 * @param askOverDuration a session longer than this is asked about
 * @param askOverBytes    a session holding more bytes than this is asked about
 */
public record DownloadWindowQuestion(Duration askOverDuration, long askOverBytes) {

    /** The key the question is asked, and its answer read back, under. */
    public static final String KEY = "downloadWindow";

    private static final String FIELD_WINDOW = "window";
    private static final String FIELD_MINUTES = "minutes";
    private static final String FIELD_START = "start";
    private static final String FIELD_END = "end";

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final long MIN_MINUTES = 1;
    private static final long DEFAULT_MINUTES = 15;

    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private static final String DECLINED = "The user declined to choose a part of the session.";
    private static final String CANCELLED = "The user dismissed the question without choosing a part of the session.";
    private static final String NO_WINDOW = "Choose which part of the session to download in the window field.";
    private static final String UNKNOWN_WINDOW =
            "'%s' is not one of the choices; choose the last hour, the last N minutes, the whole session or a custom window.";
    private static final String NO_MINUTES =
            "'The last N minutes' needs the number of minutes in the minutes field%s.";
    private static final String BAD_MINUTES = "The minutes must be a whole number%s; got %s.";
    private static final String MINUTES_RANGE = ", from 1 to %d";
    private static final String NOT_A_DATE_TIME =
            "The %s of the custom window, '%s', is not a date-time; give it as 2026-03-01T10:00:00Z.";
    private static final String NO_BOUND =
            "The custom window needs its %s: the session's own is not known.";
    private static final String ENDS_BEFORE_START =
            "The custom window ends at %s, before (or as) it starts at %s; give an end after the start.";
    private static final String OUTSIDE_SESSION =
            "The custom window from %s to %s lies outside the session, which spans %s to %s; choose a window inside it.";
    private static final String NO_SPAN =
            "The session spans no time, so it has no last stretch to take; choose the whole session.";

    public DownloadWindowQuestion {
        Objects.requireNonNull(askOverDuration, "askOverDuration");
        if (askOverDuration.isNegative()) {
            throw new IllegalArgumentException("askOverDuration must not be negative: " + askOverDuration);
        }
        if (askOverBytes < 0) {
            throw new IllegalArgumentException("askOverBytes must not be negative: " + askOverBytes);
        }
    }

    /**
     * Whether a session is large enough to ask about: longer than {@link #askOverDuration}, measured
     * to its finish or, while it is still recording, to {@code now} — or bigger than
     * {@link #askOverBytes}. A session with no start is judged by its size alone.
     */
    public boolean asks(RecordingSession session, Instant now) {
        if (session.totalSizeBytes() > askOverBytes) {
            return true;
        }
        Subject subject = new Subject(session, null, null, now);
        return subject.start() != null
                && Duration.between(subject.start(), subject.end()).compareTo(askOverDuration) > 0;
    }

    /** The question as first asked. */
    public McpToolOutcome.InputRequired ask(Subject subject) {
        return new McpToolOutcome.InputRequired(Map.of(KEY, new McpFormElicitation(subject.describe(), form(subject))));
    }

    /**
     * The question asked again after an answer that did not make a window, stating what was wrong
     * before anything else.
     */
    public McpToolOutcome.InputRequired ask(Subject subject, String problem) {
        return new McpToolOutcome.InputRequired(
                Map.of(KEY, new McpFormElicitation(problem + " " + subject.describe(), form(subject))));
    }

    /**
     * What the user answered: a window, the whole session, nothing — or something that does not make
     * a window, to be asked about again.
     */
    public WindowAnswer read(McpInputResponse response, Subject subject) {
        return switch (response.action()) {
            case DECLINE -> new WindowAnswer.NotAnswered(DECLINED);
            case CANCEL -> new WindowAnswer.NotAnswered(CANCELLED);
            case ACCEPT -> accepted(response.content(), subject);
        };
    }

    private static WindowAnswer accepted(ObjectNode content, Subject subject) {
        JsonNode chosen = content == null ? null : content.get(FIELD_WINDOW);
        if (chosen == null || !chosen.isString() || chosen.asString().isBlank()) {
            return new WindowAnswer.Incomplete(NO_WINDOW);
        }
        return Window.fromWire(chosen.asString())
                .map(window -> window.answer(content, subject))
                .orElseGet(() -> new WindowAnswer.Incomplete(UNKNOWN_WINDOW.formatted(chosen.asString())));
    }

    private static McpFormSchema form(Subject subject) {
        List<McpFormSchema.Choice> choices = Arrays.stream(Window.values())
                .map(window -> new McpFormSchema.Choice(window.wireName, window.title(subject)))
                .toList();
        Long maxMinutes = subject.spanMinutes();
        long defaultMinutes = maxMinutes == null ? DEFAULT_MINUTES : Math.min(DEFAULT_MINUTES, maxMinutes);
        return McpFormSchema.builder()
                .required(new McpFormSchema.ChoiceField(
                        new McpFormSchema.Label(FIELD_WINDOW, "Part of the session to download", null),
                        choices, Window.LAST_HOUR.wireName))
                .optional(new McpFormSchema.IntegerField(
                        new McpFormSchema.Label(FIELD_MINUTES, "Minutes",
                                "For 'The last N minutes': how many minutes, ending where the session ends"),
                        MIN_MINUTES, maxMinutes, defaultMinutes))
                .optional(new McpFormSchema.DateTimeField(
                        new McpFormSchema.Label(FIELD_START, "Start (UTC)", "For a custom window: where it starts"),
                        subject.start()))
                .optional(new McpFormSchema.DateTimeField(
                        new McpFormSchema.Label(FIELD_END, "End (UTC)", "For a custom window: where it ends"),
                        subject.end()))
                .build();
    }

    /**
     * The session being asked about, and where it is: what the question describes and every window
     * is measured against.
     *
     * @param session the session as read from its hub on this call
     * @param hub     the hub's name, as the user knows it
     * @param project the project's name on that hub
     * @param now     when the question is asked; the end of a session still recording
     */
    public record Subject(RecordingSession session, String hub, String project, Instant now) {

        public Subject {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(now, "now");
        }

        /** When the session started, or null when the hub does not say. */
        Instant start() {
            return session.createdAt();
        }

        /** When the session finished, or now while it is still recording. */
        Instant end() {
            return session.finishedAt() == null ? now : session.finishedAt();
        }

        boolean stillRecording() {
            return session.finishedAt() == null;
        }

        /** The session's length in whole minutes, rounded up and at least one; null with no start. */
        Long spanMinutes() {
            if (start() == null) {
                return null;
            }
            long millis = Math.max(0, Duration.between(start(), end()).toMillis());
            long minutes = (millis + Duration.ofMinutes(1).toMillis() - 1) / Duration.ofMinutes(1).toMillis();
            return Math.max(MIN_MINUTES, minutes);
        }

        /** The last stretch of this length, cut off at the session's start. */
        Instant startOfLast(Duration length) {
            Instant from = end().minus(length);
            return start() != null && from.isBefore(start()) ? start() : from;
        }

        String size() {
            return ByteSizes.format(session.totalSizeBytes());
        }

        String describe() {
            return "Session " + session.name() + " (" + session.id() + ") on hub " + hub + ", project " + project
                    + ", " + span() + " "
                    + "Downloading all of it moves every chunk across the network. Which part should Jeffrey download?";
        }

        /** Where the session starts and ends, how long that is and how much it holds. */
        private String span() {
            if (start() == null) {
                return "has no recorded start and holds " + size() + ".";
            }
            if (stillRecording()) {
                return "spans " + start() + " to now, " + end() + ", and is still recording ("
                        + length() + " so far, " + size() + ").";
            }
            return "spans " + start() + " to " + end() + " (" + length() + ", " + size() + ").";
        }

        private String length() {
            Duration elapsed = Duration.between(start(), end());
            if (elapsed.toHours() > 0) {
                return elapsed.toHours() + "h" + elapsed.toMinutesPart() + "m";
            }
            if (elapsed.toMinutes() > 0) {
                return elapsed.toMinutes() + "m" + elapsed.toSecondsPart() + "s";
            }
            return elapsed.toSeconds() + "s";
        }
    }

    /** The four choices; each words itself for the user and makes the answer it stands for. */
    private enum Window {

        LAST_HOUR("lastHour") {
            @Override
            String title(Subject subject) {
                return "The last hour (" + CLOCK_TIME.format(subject.startOfLast(ONE_HOUR)) + "–"
                        + CLOCK_TIME.format(subject.end()) + " UTC)";
            }

            @Override
            WindowAnswer answer(ObjectNode content, Subject subject) {
                return lastStretch(subject, ONE_HOUR);
            }
        },

        LAST_MINUTES("lastMinutes") {
            @Override
            String title(Subject subject) {
                return "The last N minutes (set Minutes below)";
            }

            @Override
            WindowAnswer answer(ObjectNode content, Subject subject) {
                Long max = subject.spanMinutes();
                String range = max == null ? "" : MINUTES_RANGE.formatted(max);
                JsonNode minutes = content.get(FIELD_MINUTES);
                if (minutes == null || minutes.isNull()) {
                    return new WindowAnswer.Incomplete(NO_MINUTES.formatted(range));
                }
                // Jackson reads a number strictly: one no long can hold is refused here, not thrown.
                if (!wholeNumber(minutes) || !minutes.canConvertToLong()) {
                    return new WindowAnswer.Incomplete(BAD_MINUTES.formatted(range, minutes));
                }
                long value = minutes.longValue();
                if (value < MIN_MINUTES || (max != null && value > max)) {
                    return new WindowAnswer.Incomplete(BAD_MINUTES.formatted(range, minutes));
                }
                // With no start there is no maximum, so the stretch can reach past what an Instant holds.
                try {
                    return lastStretch(subject, Duration.ofMinutes(value));
                } catch (ArithmeticException | DateTimeException e) {
                    return new WindowAnswer.Incomplete(BAD_MINUTES.formatted(range, minutes));
                }
            }

            private static boolean wholeNumber(JsonNode minutes) {
                return minutes.isIntegralNumber()
                        || (minutes.isNumber() && minutes.doubleValue() == Math.rint(minutes.doubleValue()));
            }
        },

        WHOLE("whole") {
            @Override
            String title(Subject subject) {
                return "The whole session (" + subject.size() + ")";
            }

            @Override
            WindowAnswer answer(ObjectNode content, Subject subject) {
                return new WindowAnswer.Whole();
            }
        },

        CUSTOM("custom") {
            @Override
            String title(Subject subject) {
                return "A custom window (set Start and End below)";
            }

            /** A bound left out is the form's default for it: the session's own start or end. */
            @Override
            WindowAnswer answer(ObjectNode content, Subject subject) {
                Bound start = bound(content, FIELD_START, subject.start());
                if (start.problem() != null) {
                    return new WindowAnswer.Incomplete(start.problem());
                }
                Bound end = bound(content, FIELD_END, subject.end());
                if (end.problem() != null) {
                    return new WindowAnswer.Incomplete(end.problem());
                }
                if (!end.value().isAfter(start.value())) {
                    return new WindowAnswer.Incomplete(ENDS_BEFORE_START.formatted(end.value(), start.value()));
                }
                boolean beforeSession = subject.start() != null && !end.value().isAfter(subject.start());
                boolean afterSession = !start.value().isBefore(subject.end());
                if (beforeSession || afterSession) {
                    return new WindowAnswer.Incomplete(OUTSIDE_SESSION.formatted(
                            start.value(), end.value(), subject.start(), subject.end()));
                }
                return new WindowAnswer.Chosen(start.value().toEpochMilli(), end.value().toEpochMilli());
            }

            private static Bound bound(ObjectNode content, String field, Instant fallback) {
                JsonNode given = content.get(field);
                if (given == null || given.isNull()) {
                    return fallback == null
                            ? new Bound(null, NO_BOUND.formatted(field))
                            : new Bound(fallback, null);
                }
                // Only a string can be a date-time; asString() on anything else throws in Jackson 3.
                if (!given.isString()) {
                    return new Bound(null, NOT_A_DATE_TIME.formatted(field, given.toString()));
                }
                try {
                    return new Bound(OffsetDateTime.parse(given.asString()).toInstant(), null);
                } catch (DateTimeParseException e) {
                    return new Bound(null, NOT_A_DATE_TIME.formatted(field, given.asString()));
                }
            }
        };

        private final String wireName;

        Window(String wireName) {
            this.wireName = wireName;
        }

        /** What the user sees for this choice. */
        abstract String title(Subject subject);

        /** The answer choosing this means, read with the rest of the form. */
        abstract WindowAnswer answer(ObjectNode content, Subject subject);

        static Optional<Window> fromWire(String wireName) {
            return Arrays.stream(values()).filter(window -> window.wireName.equals(wireName)).findFirst();
        }

        /**
         * The last stretch of this length. On a finished session a stretch reaching back to its start
         * is all of it, so it is the whole session — downloaded as the session's own local copy rather
         * than as a part. A session still recording keeps it a window up to now.
         */
        private static WindowAnswer lastStretch(Subject subject, Duration length) {
            Instant reach = subject.end().minus(length);
            boolean reachesTheStart = subject.start() != null && !reach.isAfter(subject.start());
            if (reachesTheStart && !subject.stillRecording()) {
                return new WindowAnswer.Whole();
            }
            Instant from = reachesTheStart ? subject.start() : reach;
            if (!subject.end().isAfter(from)) {
                return new WindowAnswer.Incomplete(NO_SPAN);
            }
            return new WindowAnswer.Chosen(from.toEpochMilli(), subject.end().toEpochMilli());
        }
    }

    /** One end of a custom window: its instant, or why none could be read. */
    private record Bound(Instant value, String problem) {
    }
}
