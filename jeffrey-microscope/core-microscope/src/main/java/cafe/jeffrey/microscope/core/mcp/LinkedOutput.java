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

import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
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

    /**
     * What ends a line. Written out rather than taken from the platform, as {@code MarkdownTable} does:
     * the text is read by a model, and an answer whose table rows ended one way and whose link block
     * ended another was one document in two conventions.
     */
    private static final String LINE_BREAK = "\n";
    private static final String NEXT_STEPS_BULLET = "- ";

    private static final String FOOTER_RULE = "\n\n---\n";
    private static final String FOOTER_LINK = "Open in Microscope: ";
    private static final String FOOTER_NOTE = "Link note: ";
    private static final String FOOTER_NEXT = "Next:";
    private static final String FOOTER_GUIDANCE = "Guidance:";
    private static final String CALL_SEPARATOR = " ";
    private static final String WEIGHT_OPEN = " [";
    private static final String WEIGHT_CLOSE = "]";

    /**
     * The longest guidance line the footer repeats. A longer one is advice to read with the record,
     * not a line the text should spend its budget on.
     */
    private static final int MAX_FOOTER_GUIDANCE_CHARS = 200;

    private LinkedOutput() {
    }

    /**
     * A Markdown answer with its footer, rendered from the record its structured content is: the body
     * capped with the footer's length reserved, then the footer. A host may hand the model only the
     * text, so the text has to carry the link and the next calls too; reserving the footer's length is
     * what keeps it whole when the envelope caps the full text again.
     *
     * @param followUp   the record's follow-up; each next call becomes one {@code Next:} line, ending
     *                   with the call's weight in brackets
     * @param uiLink     the record's page for the user
     * @param uiLinkNote what the link cannot reproduce, or {@code null}
     */
    public static Footed footed(String body, McpFollowUp followUp, String uiLink, String uiLinkNote) {
        String footer = footer(followUp, uiLink, uiLinkNote);
        int budget = McpToolOutput.MAX_CHARS - footer.length();
        String text = body == null ? "" : body;
        return new Footed(McpToolOutput.cappedWithin(text, budget) + footer, text.length() > budget);
    }

    /**
     * The footer alone, for an answer whose body is sized to fit before it is rendered - a page of
     * rows that is measured, never cut - and so needs no cap of its own.
     *
     * @param uiLink the record's page for the user, or {@code null} for a tool whose subject has no
     *               page; the footer then holds the next calls alone
     */
    public static String footer(McpFollowUp followUp, String uiLink, String uiLinkNote) {
        List<String> lines = new ArrayList<>();
        if (uiLink != null) {
            lines.add(FOOTER_LINK + uiLink);
        }
        if (uiLinkNote != null) {
            lines.add(FOOTER_NOTE + uiLinkNote);
        }
        if (!followUp.nextTools().isEmpty()) {
            lines.add(FOOTER_NEXT);
            for (McpNextTool call : followUp.nextTools()) {
                lines.add(NEXT_STEPS_BULLET + call.tool() + CALL_SEPARATOR + Json.toString(call.arguments())
                        + WEIGHT_OPEN + call.weight().name() + WEIGHT_CLOSE);
            }
        }
        List<String> shortGuidance = followUp.guidance().stream()
                .filter(line -> line.length() <= MAX_FOOTER_GUIDANCE_CHARS)
                .toList();
        if (!shortGuidance.isEmpty()) {
            lines.add(FOOTER_GUIDANCE);
            for (String line : shortGuidance) {
                lines.add(NEXT_STEPS_BULLET + line);
            }
        }
        return lines.isEmpty() ? "" : FOOTER_RULE + String.join(LINE_BREAK, lines);
    }

    /**
     * A footed answer's text, and whether its body had to be cut to leave the footer room.
     *
     * @param truncated whether the body exceeded the budget the footer left it
     */
    public record Footed(String text, boolean truncated) {
    }
}
