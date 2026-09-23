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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolOutput;

import java.util.List;

/**
 * A tool answer with a link to the view it describes, for the person reading it.
 * <p>
 * The link exists for the reader, not the model: a URL cannot be analysed further and does not help
 * choose the next tool. That is exactly why it travels with an answer the model already produces
 * instead of behind a tool of its own — a model weighing its own context would rightly skip a call
 * whose result it cannot use.
 * <p>
 * The body is capped <em>before</em> the link is appended, which is the whole reason this exists as a
 * type rather than as string concatenation at each call site: {@link McpToolOutput#capped(String)}
 * truncates at its limit, so a link appended first is silently cut off exactly the oversized answers
 * whose reader most needs the interactive view.
 */
public final class LinkedOutput {

    private static final String LINK_BLOCK = "%s%n%n---%nOpen in Jeffrey: %s";
    private static final String LINK_BLOCK_WITH_NOTE = "%s%n%n---%nOpen in Jeffrey (%s): %s";
    private static final String NEXT_STEPS_HEADING = "Where to go next:";
    private static final String NEXT_STEPS_BULLET = "- ";

    private LinkedOutput() {
    }

    /**
     * The answer, capped, followed by the link.
     */
    public static String of(String body, String url) {
        return LINK_BLOCK.formatted(McpToolOutput.capped(body), url);
    }

    /**
     * The same, for a link that cannot reproduce everything the answer was built with — the note says
     * what the reader will see instead, rather than letting a URL quietly show a different view.
     */
    public static String of(String body, String url, String note) {
        return LINK_BLOCK_WITH_NOTE.formatted(McpToolOutput.capped(body), note, url);
    }

    /**
     * The answer, capped, then where to go next, then the link.
     * <p>
     * A Markdown export has no field to put routing in the way a JSON answer does, so it goes in the
     * same trailing block as the link - after the cap, for the same reason the link is: an oversized
     * export is exactly the one whose reader most needs to know what to do with it.
     */
    public static String of(String body, List<String> nextSteps, String url) {
        return LINK_BLOCK.formatted(cappedWithNextSteps(body, nextSteps), url);
    }

    /**
     * The same, for a link that cannot reproduce everything the answer was built with.
     */
    public static String of(String body, List<String> nextSteps, String url, String note) {
        return LINK_BLOCK_WITH_NOTE.formatted(cappedWithNextSteps(body, nextSteps), note, url);
    }

    /**
     * The body capped, with the routing after it — capped exactly once.
     * <p>
     * Capping twice is what this avoids, and it is not a hypothetical: an oversized body comes back
     * from {@link McpToolOutput#capped(String)} already <em>at</em> the limit plus its truncation
     * note, so appending anything and capping again cuts the note and the steps straight back off.
     * The steps were being dropped from precisely the answers they were added for.
     */
    private static String cappedWithNextSteps(String body, List<String> nextSteps) {
        String capped = McpToolOutput.capped(body);
        if (nextSteps.isEmpty()) {
            return capped;
        }

        StringBuilder builder = new StringBuilder(capped)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(NEXT_STEPS_HEADING);
        for (String step : nextSteps) {
            builder.append(System.lineSeparator()).append(NEXT_STEPS_BULLET).append(step);
        }
        return builder.toString();
    }

    /**
     * A value rendered as JSON, capped. The link belongs <em>inside</em> the value here - as a
     * {@code uiLink} field on the record being returned - because appending it as text would leave
     * the answer no longer parseable as JSON.
     */
    public static String json(Object value) {
        return McpToolOutput.json(value);
    }
}
