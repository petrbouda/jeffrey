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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpFormElicitation;
import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.storage.recording.api.file.Recording;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

/**
 * The confirmation {@code recordings_delete} asks a client that renders forms for, before it removes
 * a recording and the profile built from it.
 * <p>
 * One required box, unchecked to begin with. Only a checked box deletes; a box left unchecked, a
 * decline and a dismissal all withhold consent, and an answer that says neither — no box at all, or
 * not a yes/no — is asked again, as the specification asks for missing input.
 */
final class DeleteConfirmation {

    /** The key the question is asked, and its answer read back, under. */
    static final String KEY = "confirmDeletion";

    /** What a withheld deletion answers with, whichever way consent was withheld. */
    static final String NOT_CONFIRMED = "The user did not confirm; nothing was deleted.";

    private static final String FIELD_CONFIRM = "confirm";

    private static final McpFormSchema FORM = McpFormSchema.builder()
            .required(new McpFormSchema.BooleanField(
                    new McpFormSchema.Label(FIELD_CONFIRM, "Delete this recording",
                            "Check to delete the recording and everything analysed out of it"),
                    false))
            .build();

    private DeleteConfirmation() {
    }

    /**
     * The question: what goes, by name, id and profile, and that a copy on a hub stays where it is.
     */
    static McpToolOutcome.InputRequired ask(Recording recording) {
        String profile = recording.hasProfile()
                ? "the profile " + recording.profileId() + " built from it"
                : "its files (it has no profile yet)";
        String message = "Delete recording " + recording.recordingName() + " (" + recording.id() + ") and "
                + profile + ", with everything analysed out of it? This cannot be undone here. A copy of "
                + "the recording on a Jeffrey Hub is untouched, and hubs_download can pull it again.";
        return new McpToolOutcome.InputRequired(Map.of(KEY, new McpFormElicitation(message, FORM)));
    }

    /** What the retried call says the user answered. */
    static Confirmation read(McpCallContext call) {
        Optional<McpInputResponse> response = call.inputResponse(KEY);
        if (response.isEmpty()) {
            return new Confirmation.Unanswered();
        }
        return switch (response.get().action()) {
            case DECLINE, CANCEL -> new Confirmation.Withheld(NOT_CONFIRMED);
            case ACCEPT -> fromBox(response.get().content());
        };
    }

    private static Confirmation fromBox(ObjectNode content) {
        JsonNode box = content == null ? null : content.get(FIELD_CONFIRM);
        if (box == null || !box.isBoolean()) {
            return new Confirmation.Unanswered();
        }
        return box.asBoolean() ? new Confirmation.Confirmed() : new Confirmation.Withheld(NOT_CONFIRMED);
    }

    /** The user's say on one deletion. */
    sealed interface Confirmation {

        /** The box was checked: delete. */
        record Confirmed() implements Confirmation {
        }

        /**
         * The user said no, one way or another: nothing is deleted.
         *
         * @param reason what the caller is told
         */
        record Withheld(String reason) implements Confirmation {

            public Withheld {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("A withheld deletion needs its reason");
                }
            }
        }

        /** No usable answer yet: ask. */
        record Unanswered() implements Confirmation {
        }
    }
}
