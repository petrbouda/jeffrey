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

import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A part of a hub session chosen by name — the windows a call to hubs_download can name and the
 * choices of the question Jeffrey asks about a large session, resolved by the same rules so a chat
 * answer and a form answer download the same thing.
 * <p>
 * Each constant carries its rule and the arguments it takes; the constants themselves have no
 * bodies, because the enum is also an output type and the schema generator refuses those.
 */
public enum DownloadWindow {

    WHOLE(new WindowRule.Whole(), Set.of(), Set.of()),
    LAST_MINUTES(new WindowRule.LastMinutes(), Set.of(WindowParam.MINUTES), Set.of()),
    STARTUP(new WindowRule.Startup(), Set.of(), Set.of()),
    LATEST(new WindowRule.Latest(), Set.of(), Set.of()),
    PEAK(new WindowRule.Peak(), Set.of(), Set.of()),
    BEFORE(new WindowRule.Before(), Set.of(WindowParam.AT, WindowParam.MINUTES), Set.of(WindowParam.AT)),
    AROUND(new WindowRule.Around(), Set.of(WindowParam.AT, WindowParam.MINUTES), Set.of(WindowParam.AT)),
    CUSTOM(new WindowRule.Custom(), Set.of(WindowParam.START, WindowParam.END),
            Set.of(WindowParam.START, WindowParam.END));

    /** The minutes LAST_MINUTES, BEFORE and AROUND take when none are given: the last hour. */
    public static final int DEFAULT_MINUTES = 60;

    /** Only a guard against arithmetic overflow: a year of minutes. */
    public static final int MAX_WINDOW_MINUTES = 366 * 24 * 60;

    private static final String DOES_NOT_TAKE = "window %s does not take %s; %s goes with %s.";
    private static final String NEEDS = "window %s needs %s.";
    private static final String WITHOUT_WINDOW = "%s goes with window %s; name the window too.";
    private static final String OR = " or ";
    private static final String LIST = ", ";

    private final WindowRule rule;
    private final Set<WindowParam> takes;
    private final Set<WindowParam> needsOneOf;

    DownloadWindow(WindowRule rule, Set<WindowParam> takes, Set<WindowParam> needsOneOf) {
        this.rule = rule;
        this.takes = takes;
        this.needsOneOf = needsOneOf;
    }

    /**
     * Refuses a call that gives this window an argument it does not take, or leaves out the one it
     * needs — before anything crosses the network. The form is never checked this way: it only reads
     * the fields its chosen window takes.
     */
    public void checkArguments(WindowArguments given) {
        for (WindowParam param : given.present()) {
            if (!takes.contains(param)) {
                throw new IllegalArgumentException(DOES_NOT_TAKE.formatted(
                        name(), param.argument(), param.argument(), names(taking(param))));
            }
        }
        boolean needed = !needsOneOf.isEmpty() && needsOneOf.stream().noneMatch(given.present()::contains);
        if (needed) {
            throw new IllegalArgumentException(NEEDS.formatted(name(), needsOneOf.stream()
                    .map(WindowParam::argument).sorted().collect(Collectors.joining(OR))));
        }
    }

    /**
     * Refuses an argument that only a named window takes when no window was named; the bounds are
     * the exception, since bounds without a window have always meant a custom span.
     */
    public static void checkWithoutWindow(WindowArguments given) {
        for (WindowParam param : List.of(WindowParam.MINUTES, WindowParam.AT)) {
            if (given.present().contains(param)) {
                throw new IllegalArgumentException(
                        WITHOUT_WINDOW.formatted(param.argument(), names(taking(param))));
            }
        }
    }

    /**
     * The part this window is on the session. The session is read only when the rule needs it, so a
     * window that is a fixed span or the whole session costs no call to the hub.
     */
    public WindowResolution resolve(WindowArguments given, Supplier<WindowSubject> subject) {
        return rule.resolve(this, given, subject);
    }

    /** The form's choice for this window on this session; empty when the session cannot offer it. */
    public Optional<McpFormSchema.Choice> choice(WindowSubject subject) {
        return rule.title(subject).map(title -> new McpFormSchema.Choice(name(), title));
    }

    /** Whether this window reads the given argument. */
    public boolean takes(WindowParam param) {
        return takes.contains(param);
    }

    static List<DownloadWindow> taking(WindowParam param) {
        return Arrays.stream(values()).filter(window -> window.takes.contains(param)).toList();
    }

    private static String names(List<DownloadWindow> windows) {
        return windows.stream().map(Enum::name).collect(Collectors.joining(LIST));
    }
}
