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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;

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
        return of(appendNextSteps(McpToolOutput.capped(body), nextSteps), url);
    }

    /**
     * The same, for a link that cannot reproduce everything the answer was built with.
     */
    public static String of(String body, List<String> nextSteps, String url, String note) {
        return of(appendNextSteps(McpToolOutput.capped(body), nextSteps), url, note);
    }

    /**
     * Appended to an already-capped body: {@link #of(String, String)} caps again, which is a no-op on
     * a string that already fits and keeps these steps out of the truncated region either way.
     */
    private static String appendNextSteps(String cappedBody, List<String> nextSteps) {
        if (nextSteps.isEmpty()) {
            return cappedBody;
        }

        StringBuilder builder = new StringBuilder(cappedBody)
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
