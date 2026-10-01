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
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.ChosenWindow;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * How one {@link DownloadWindow} resolves on a session, and how the form offers it. A rule that
 * needs nothing of the session never reads it, so such a window costs no call to the hub.
 */
sealed interface WindowRule {

    /**
     * @param window  the window this rule belongs to, for the evidence it reports
     * @param given   what the window was given beside its name
     * @param subject the session, read only by the rules that need it
     */
    WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject);

    /** The form's title for this window on this session; empty when the session cannot offer it. */
    Optional<String> title(WindowSubject subject);

    record Whole() implements WindowRule {

        private static final String EVIDENCE = "the whole session";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            return new WindowResolution.Whole(ChosenWindow.span(window, EVIDENCE));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return Optional.of("The whole session (" + subject.size() + ")");
        }
    }

    /** The last minutes up to the session's end — now while it still records. */
    record LastMinutes() implements WindowRule {

        private static final String NO_SPAN =
                "The session spans no time, so it has no last stretch to take; choose the whole session.";
        private static final String EVIDENCE = "the last %d minutes, up to %s";
        private static final String REACHES_THE_START = "the last %d minutes reach back to the session's start";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            Optional<Long> minutes = WindowTexts.minutes(given);
            if (minutes.isEmpty()) {
                return WindowTexts.minutesOutOfRange(given);
            }
            WindowSubject session = subject.get();
            Instant end = session.end();
            Instant reach = end.minus(Duration.ofMinutes(minutes.get()));
            boolean reachesTheStart = session.start() != null && !reach.isAfter(session.start());
            if (reachesTheStart && !session.stillRecording()) {
                return new WindowResolution.Whole(ChosenWindow.span(window, REACHES_THE_START.formatted(minutes.get())));
            }
            Instant from = reachesTheStart ? session.start() : reach;
            if (!end.isAfter(from)) {
                return new WindowResolution.Refused(NO_SPAN);
            }
            return new WindowResolution.Span(new ChunkWindow(from, end),
                    ChosenWindow.span(window, EVIDENCE.formatted(minutes.get(), end)));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            Instant end = subject.end();
            Instant from = end.minus(Duration.ofMinutes(DownloadWindow.DEFAULT_MINUTES));
            if (subject.start() != null && from.isBefore(subject.start())) {
                from = subject.start();
            }
            return Optional.of("The last N minutes (set Minutes; " + DownloadWindow.DEFAULT_MINUTES + " is "
                    + WindowTexts.at(from, subject) + "–" + WindowTexts.at(end, subject) + WindowTexts.UTC + ")");
        }
    }

    /** The session's first chunk, unless the hub's cleaners have removed it. */
    record Startup() implements WindowRule {

        private static final String STARTUP_UNKNOWN =
                "Whether the session's first chunk is still on the hub cannot be told: %s. Choose a window.";
        private static final String EVIDENCE = "the session's first chunk, from %s";
        private static final String GONE = "The hub no longer holds the session's first chunk: the session started at "
                + "%s and the oldest chunk kept, %s, starts at %s, more than one chunk (%s) later.";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            WindowSubject session = subject.get();
            return switch (session.chunks().startup()) {
                case SessionChunks.Startup.Kept kept -> new WindowResolution.OneChunk(kept.chunk().file().id(),
                        WindowTexts.chunkChosen(window, EVIDENCE.formatted(kept.chunk().start()), kept.chunk(), null, session));
                case SessionChunks.Startup.Gone gone -> {
                    String reason = GONE.formatted(session.start(), gone.oldest().file().name(),
                            gone.oldest().start(), WindowSubject.length(gone.chunkLength()));
                    yield new WindowResolution.NotRetained(
                            WindowTexts.chunkChosen(window, reason, gone.oldest(), null, session), reason);
                }
                case SessionChunks.Startup.Unknown unknown ->
                        new WindowResolution.Refused(STARTUP_UNKNOWN.formatted(unknown.why()));
            };
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            if (subject.chunks().startup() instanceof SessionChunks.Startup.Kept kept) {
                return Optional.of("Startup (" + WindowTexts.chunkSpan(kept.chunk(), subject) + ", "
                        + ByteSizes.format(kept.chunk().file().size()) + ")");
            }
            return Optional.empty();
        }
    }

    /** The newest finished chunk; never the one still being written. */
    record Latest() implements WindowRule {

        private static final String NO_FINISHED_CHUNK =
                "The session has no finished chunk yet, so there is no %s to take; choose a window.";
        private static final String EVIDENCE = "the newest finished chunk, from %s";
        private static final String WHAT = "latest chunk";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            WindowSubject session = subject.get();
            return session.chunks().latest()
                    .<WindowResolution>map(chunk -> new WindowResolution.OneChunk(chunk.file().id(),
                            WindowTexts.chunkChosen(window, EVIDENCE.formatted(chunk.start()), chunk, null, session)))
                    .orElseGet(() -> new WindowResolution.Refused(NO_FINISHED_CHUNK.formatted(WHAT)));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return subject.chunks().latest().map(chunk -> "Latest finished chunk (" + WindowTexts.chunkSpan(chunk, subject)
                    + ", " + ByteSizes.format(chunk.file().size()) + ")");
        }
    }

    /** The largest compressed chunk: the busiest stretch, by how much it recorded. */
    record Peak() implements WindowRule {

        private static final String NONE_COMPRESSED = "PEAK compares the chunks the hub has compressed, and none of "
                + "this session's is compressed yet; choose LATEST or a window.";
        private static final String EVIDENCE = "the largest of %d compressed chunks, from %s: %s, %s× the median %s%s%s";
        private static final String TIED = "; another chunk is as large, so the most recent was taken";
        private static final String NOT_COMPRESSED = "; %d newest chunk(s) not compared: not compressed yet";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            WindowSubject session = subject.get();
            return session.chunks().peak()
                    .<WindowResolution>map(peak -> new WindowResolution.OneChunk(peak.chunk().file().id(),
                            WindowTexts.chunkChosen(window, evidence(peak), peak.chunk(), peak.medianSizeBytes(), session)))
                    .orElseGet(() -> new WindowResolution.Refused(NONE_COMPRESSED));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return subject.chunks().peak().map(peak -> "Peak (" + WindowTexts.chunkSpan(peak.chunk(), subject) + ", "
                    + ByteSizes.format(peak.chunk().file().size()) + ", " + ratio(peak) + "× median)");
        }

        private static String evidence(SessionChunks.Peak peak) {
            return EVIDENCE.formatted(peak.compared(), peak.chunk().start(),
                    ByteSizes.format(peak.chunk().file().size()), ratio(peak), ByteSizes.format(peak.medianSizeBytes()),
                    peak.tied() ? TIED : "",
                    peak.notCompressed() > 0 ? NOT_COMPRESSED.formatted(peak.notCompressed()) : "");
        }

        private static String ratio(SessionChunks.Peak peak) {
            double ratio = peak.medianSizeBytes() == 0 ? 1.0 : (double) peak.chunk().file().size() / peak.medianSizeBytes();
            return String.format(Locale.ROOT, "%.1f", ratio);
        }
    }

    /** The minutes ending at a moment — before a heap dump, a crash file, a deploy. */
    record Before() implements WindowRule {

        private static final String NO_AT = "%s needs atEpochMs, the moment to take the minutes %s.";
        private static final String EVIDENCE = "the %d minutes before %s";
        private static final String WHEN = "before";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            if (given.atEpochMs() == null) {
                return new WindowResolution.Refused(NO_AT.formatted(window.name(), WHEN));
            }
            Optional<Long> minutes = WindowTexts.minutes(given);
            if (minutes.isEmpty()) {
                return WindowTexts.minutesOutOfRange(given);
            }
            Instant at = Instant.ofEpochMilli(given.atEpochMs());
            return new WindowResolution.Span(new ChunkWindow(at.minus(Duration.ofMinutes(minutes.get())), at),
                    ChosenWindow.span(window, EVIDENCE.formatted(minutes.get(), at)));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return Optional.of("Before a moment (set At and Minutes; e.g. when a heap dump or crash file was written)");
        }
    }

    /** The minutes centred on a moment. */
    record Around() implements WindowRule {

        private static final String NO_AT = "%s needs atEpochMs, the moment to take the minutes %s.";
        private static final String EVIDENCE = "the %d minutes around %s";
        private static final String WHEN = "around";
        private static final long HALF_MINUTE_MS = Duration.ofSeconds(30).toMillis();

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            if (given.atEpochMs() == null) {
                return new WindowResolution.Refused(NO_AT.formatted(window.name(), WHEN));
            }
            Optional<Long> minutes = WindowTexts.minutes(given);
            if (minutes.isEmpty()) {
                return WindowTexts.minutesOutOfRange(given);
            }
            long half = minutes.get() * HALF_MINUTE_MS;
            return new WindowResolution.Span(
                    ChunkWindow.ofEpochMillis(given.atEpochMs() - half, given.atEpochMs() + half),
                    ChosenWindow.span(window, EVIDENCE.formatted(minutes.get(), Instant.ofEpochMilli(given.atEpochMs()))));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return Optional.of("Around a moment (set At and Minutes)");
        }
    }

    /** A span the caller names; a bound left out stays open. */
    record Custom() implements WindowRule {

        private static final String NO_BOUND = "CUSTOM needs startEpochMs, endEpochMs or both.";
        private static final String ENDS_BEFORE_START =
                "The window ends at %s, before (or as) it starts at %s; give an end after the start.";
        private static final String EVIDENCE = "the window the call named";

        @Override
        public WindowResolution resolve(DownloadWindow window, WindowArguments given, Supplier<WindowSubject> subject) {
            if (given.startEpochMs() == null && given.endEpochMs() == null) {
                return new WindowResolution.Refused(NO_BOUND);
            }
            if (given.startEpochMs() != null && given.endEpochMs() != null
                    && given.endEpochMs() <= given.startEpochMs()) {
                return new WindowResolution.Refused(ENDS_BEFORE_START.formatted(
                        Instant.ofEpochMilli(given.endEpochMs()), Instant.ofEpochMilli(given.startEpochMs())));
            }
            return new WindowResolution.Span(ChunkWindow.ofEpochMillis(given.startEpochMs(), given.endEpochMs()),
                    ChosenWindow.span(window, EVIDENCE));
        }

        @Override
        public Optional<String> title(WindowSubject subject) {
            return Optional.of("A custom window (set Start and End)");
        }
    }
}
