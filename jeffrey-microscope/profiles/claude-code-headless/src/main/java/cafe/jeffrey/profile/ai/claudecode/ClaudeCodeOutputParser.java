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

package cafe.jeffrey.profile.ai.claudecode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses the {@code stream-json} (JSONL) output of a headless Claude Code invocation into a
 * {@link ClaudeCodeResult}: the final assistant text from the terminal {@code result} event, plus the
 * names of any tools the model invoked. Tolerant of both the message-level event shape (default) and
 * the lower-level {@code stream_event} shape (emitted with {@code --include-partial-messages}).
 */
final class ClaudeCodeOutputParser {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeCodeOutputParser.class);

    private static final String EVENT_TYPE = "type";
    private static final String EVENT_TYPE_ASSISTANT = "assistant";
    private static final String EVENT_TYPE_STREAM_EVENT = "stream_event";
    private static final String EVENT_TYPE_RESULT = "result";
    private static final String CONTENT_TYPE_TOOL_USE = "tool_use";
    private static final String RESULT_SUBTYPE_SUCCESS = "success";

    ClaudeCodeResult parse(List<String> lines) {
        Set<String> toolsUsed = new LinkedHashSet<>();
        String resultText = "";
        boolean error = false;

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = Json.readTree(line);
            } catch (RuntimeException e) {
                LOG.debug("Skipping non-JSON line from Claude Code output: line={}", line);
                continue;
            }

            String type = node.path(EVENT_TYPE).asString("");
            switch (type) {
                case EVENT_TYPE_ASSISTANT -> collectAssistantToolUses(node, toolsUsed);
                case EVENT_TYPE_STREAM_EVENT -> collectStreamEventToolUse(node, toolsUsed);
                case EVENT_TYPE_RESULT -> {
                    resultText = node.path(EVENT_TYPE_RESULT).asString("");
                    error = node.path("is_error").asBoolean(false)
                            || !RESULT_SUBTYPE_SUCCESS.equals(node.path("subtype").asString(RESULT_SUBTYPE_SUCCESS));
                }
                default -> {
                    // system/init and other events carry no result text or tool usage.
                }
            }
        }

        if (resultText.isBlank() && error) {
            return ClaudeCodeResult.failure("Claude Code reported an error without a textual result.");
        }
        return new ClaudeCodeResult(resultText, error, List.copyOf(toolsUsed));
    }

    private static void collectAssistantToolUses(JsonNode assistantEvent, Set<String> toolsUsed) {
        JsonNode content = assistantEvent.path("message").path("content");
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            if (CONTENT_TYPE_TOOL_USE.equals(block.path(EVENT_TYPE).asString(""))) {
                addToolName(block.path("name").asString(""), toolsUsed);
            }
        }
    }

    private static void collectStreamEventToolUse(JsonNode streamEvent, Set<String> toolsUsed) {
        JsonNode contentBlock = streamEvent.path("event").path("content_block");
        if (CONTENT_TYPE_TOOL_USE.equals(contentBlock.path(EVENT_TYPE).asString(""))) {
            addToolName(contentBlock.path("name").asString(""), toolsUsed);
        }
    }

    private static void addToolName(String name, Set<String> toolsUsed) {
        if (!name.isBlank()) {
            toolsUsed.add(name);
        }
    }
}
