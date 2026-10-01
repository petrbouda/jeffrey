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

import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.ChosenWindow;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;

import java.util.Objects;

/**
 * What a predefined window resolves to on one session — the same whether the window came as a tool
 * argument or as the user's answer to the form.
 */
public sealed interface WindowResolution {

    /** A span of time: every chunk it touches. */
    record Span(ChunkWindow window, ChosenWindow chosen) implements WindowResolution {

        public Span {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(chosen, "chosen");
        }
    }

    /** One chunk, by its file id: exact, and the same download as naming that file. */
    record OneChunk(String fileId, ChosenWindow chosen) implements WindowResolution {

        public OneChunk {
            Objects.requireNonNull(fileId, "fileId");
            Objects.requireNonNull(chosen, "chosen");
        }
    }

    /** The whole session. */
    record Whole(ChosenWindow chosen) implements WindowResolution {

        public Whole {
            Objects.requireNonNull(chosen, "chosen");
        }
    }

    /** STARTUP on a session whose first chunk the hub no longer holds; nothing is substituted. */
    record NotRetained(ChosenWindow chosen, String reason) implements WindowResolution {

        public NotRetained {
            Objects.requireNonNull(chosen, "chosen");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The window cannot be taken on this session as given; the problem says what to change. */
    record Refused(String problem) implements WindowResolution {

        public Refused {
            if (problem == null || problem.isBlank()) {
                throw new IllegalArgumentException("a refusal needs its problem stated");
            }
        }
    }
}
