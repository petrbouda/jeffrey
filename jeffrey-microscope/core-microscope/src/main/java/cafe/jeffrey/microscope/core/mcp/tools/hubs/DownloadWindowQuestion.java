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

import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The question {@code hubs_download} puts to the user before it moves a large hub session across the
 * network: which part of it to bring. The choices are the {@link DownloadWindow}s this session can
 * offer — the whole of it, the last minutes, its startup, its latest or its largest chunk, the
 * minutes before or around a moment, or a window of their own — each titled with its figures.
 * <p>
 * Asked only about a session that runs longer than {@link #askOverDuration} or holds more than
 * {@link #askOverBytes}; anything smaller is downloaded whole, as it always was. The answer is
 * resolved by the same rules as a window the call names, on the session as read on each call, so a
 * retry answers about the session as it stands then.
 * <p>
 * The form is flat and primitive, as the elicitation specification requires: one titled single
 * choice, one bounded integer and three {@code date-time} strings, all with defaults.
 *
 * @param askOverDuration a session longer than this is asked about
 * @param askOverBytes    a session holding more bytes than this is asked about
 */
public record DownloadWindowQuestion(Duration askOverDuration, long askOverBytes) {

    /** The key the question is asked, and its answer read back, under. */
    public static final String KEY = "downloadWindow";

    private static final String FIELD_WINDOW = "window";
    private static final Map<WindowParam, String> FIELDS = Map.of(
            WindowParam.MINUTES, "minutes",
            WindowParam.AT, "at",
            WindowParam.START, "start",
            WindowParam.END, "end");

    private static final String DECLINED = "The user declined to choose a part of the session.";
    private static final String CANCELLED = "The user dismissed the question without choosing a part of the session.";
    private static final String NO_WINDOW = "Choose which part of the session to download in the window field.";
    private static final String UNKNOWN_WINDOW = "'%s' is not one of the choices; choose one of %s.";
    private static final String BAD_MINUTES = "The minutes must be a whole number from 1 to %d; got %s.";
    private static final String NOT_A_DATE_TIME = "The %s, '%s', is not a date-time; give it as 2026-03-01T10:00:00Z.";
    private static final String CHOICE_SEPARATOR = ", ";

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
        WindowSubject subject = new WindowSubject(session, null, null, now);
        return subject.start() != null
                && Duration.between(subject.start(), subject.end()).compareTo(askOverDuration) > 0;
    }

    /** The question as first asked. */
    public McpToolOutcome.InputRequired ask(WindowSubject subject) {
        return new McpToolOutcome.InputRequired(Map.of(KEY, new McpFormElicitation(subject.describe(), form(subject))));
    }

    /**
     * The question asked again after an answer that did not make a window, stating what was wrong
     * first.
     */
    public McpToolOutcome.InputRequired ask(WindowSubject subject, String problem) {
        return new McpToolOutcome.InputRequired(
                Map.of(KEY, new McpFormElicitation(problem + " " + subject.describe(), form(subject))));
    }

    /**
     * What the user's answer means: the window chosen with the fields it takes, nothing at all, or a
     * malformed answer that the question is asked again about. A malformed field never throws.
     */
    public WindowAnswer read(McpInputResponse response, WindowSubject subject) {
        return switch (response.action()) {
            case DECLINE -> new WindowAnswer.NotAnswered(DECLINED);
            case CANCEL -> new WindowAnswer.NotAnswered(CANCELLED);
            case ACCEPT -> accepted(response.content(), subject);
        };
    }

    private static WindowAnswer accepted(ObjectNode content, WindowSubject subject) {
        JsonNode chosen = content == null ? null : content.get(FIELD_WINDOW);
        if (chosen == null || !chosen.isString() || chosen.asString().isBlank()) {
            return new WindowAnswer.Incomplete(NO_WINDOW);
        }
        Optional<DownloadWindow> window = offered(subject).stream()
                .filter(offered -> offered.name().equals(chosen.asString()))
                .findFirst();
        if (window.isEmpty()) {
            return new WindowAnswer.Incomplete(UNKNOWN_WINDOW.formatted(chosen.asString(), offered(subject).stream()
                    .map(Enum::name).collect(Collectors.joining(CHOICE_SEPARATOR))));
        }
        return fields(content, window.get());
    }

    /** The fields the chosen window takes, read leniently enough that nothing malformed throws. */
    private static WindowAnswer fields(ObjectNode content, DownloadWindow window) {
        Field<Integer> minutes = window.takes(WindowParam.MINUTES)
                ? minutes(content.get(FIELDS.get(WindowParam.MINUTES)))
                : Field.absent();
        Field<Long> at = dateTime(content, window, WindowParam.AT);
        Field<Long> start = dateTime(content, window, WindowParam.START);
        Field<Long> end = dateTime(content, window, WindowParam.END);
        for (Field<?> field : List.of(minutes, at, start, end)) {
            if (field.problem() != null) {
                return new WindowAnswer.Incomplete(field.problem());
            }
        }
        return new WindowAnswer.Chosen(window,
                new WindowArguments(minutes.value(), at.value(), start.value(), end.value()));
    }

    /** One form field as read: its value, or the problem with it; both null when it was not given. */
    private record Field<T>(T value, String problem) {

        static <T> Field<T> absent() {
            return new Field<>(null, null);
        }
    }

    private static Field<Integer> minutes(JsonNode given) {
        if (given == null || given.isNull()) {
            return Field.absent();
        }
        return wholeMinutes(given)
                .map(minutes -> new Field<>(minutes, null))
                .orElseGet(() -> new Field<>(null, BAD_MINUTES.formatted(DownloadWindow.MAX_WINDOW_MINUTES, given)));
    }

    private static Field<Long> dateTime(ObjectNode content, DownloadWindow window, WindowParam param) {
        String name = FIELDS.get(param);
        JsonNode given = content.get(name);
        if (!window.takes(param) || given == null || given.isNull()) {
            return Field.absent();
        }
        // Only a string can be a date-time; asString() on anything else throws in Jackson 3.
        if (!given.isString()) {
            return new Field<>(null, NOT_A_DATE_TIME.formatted(name, given.toString()));
        }
        try {
            return new Field<>(OffsetDateTime.parse(given.asString()).toInstant().toEpochMilli(), null);
        } catch (DateTimeParseException | ArithmeticException e) {
            return new Field<>(null, NOT_A_DATE_TIME.formatted(name, given.asString()));
        }
    }

    private static Optional<Integer> wholeMinutes(JsonNode minutes) {
        // Jackson reads a number strictly: one no int can hold is refused here, not thrown.
        boolean whole = minutes.isIntegralNumber()
                || (minutes.isNumber() && minutes.doubleValue() == Math.rint(minutes.doubleValue()));
        if (!whole || !minutes.canConvertToLong()) {
            return Optional.empty();
        }
        long value = minutes.longValue();
        return value < 1 || value > DownloadWindow.MAX_WINDOW_MINUTES ? Optional.empty() : Optional.of((int) value);
    }

    private static List<DownloadWindow> offered(WindowSubject subject) {
        return Arrays.stream(DownloadWindow.values())
                .filter(window -> window.choice(subject).isPresent())
                .toList();
    }

    private static McpFormSchema form(WindowSubject subject) {
        List<McpFormSchema.Choice> choices = Arrays.stream(DownloadWindow.values())
                .map(window -> window.choice(subject))
                .flatMap(Optional::stream)
                .toList();
        return McpFormSchema.builder()
                .required(new McpFormSchema.ChoiceField(
                        new McpFormSchema.Label(FIELD_WINDOW, "Part of the session to download", null),
                        choices, DownloadWindow.LAST_MINUTES.name()))
                .optional(new McpFormSchema.IntegerField(
                        new McpFormSchema.Label(FIELDS.get(WindowParam.MINUTES), "Minutes",
                                "For the last N minutes, Before and Around: how many minutes"),
                        1L, (long) DownloadWindow.MAX_WINDOW_MINUTES, (long) DownloadWindow.DEFAULT_MINUTES))
                .optional(new McpFormSchema.DateTimeField(
                        new McpFormSchema.Label(FIELDS.get(WindowParam.AT), "At (UTC)",
                                "For Before and Around: the moment, e.g. when a heap dump or crash file was written"),
                        subject.end()))
                .optional(new McpFormSchema.DateTimeField(
                        new McpFormSchema.Label(FIELDS.get(WindowParam.START), "Start (UTC)",
                                "For a custom window: where it starts"),
                        subject.start()))
                .optional(new McpFormSchema.DateTimeField(
                        new McpFormSchema.Label(FIELDS.get(WindowParam.END), "End (UTC)",
                                "For a custom window: where it ends"),
                        subject.end()))
                .build();
    }
}
