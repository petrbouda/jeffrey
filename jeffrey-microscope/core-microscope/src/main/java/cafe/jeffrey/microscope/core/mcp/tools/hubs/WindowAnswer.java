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

/**
 * What the user answered when asked which part of a large hub session {@code hubs_download} should
 * bring, as {@link DownloadWindowQuestion#read} makes of it.
 * <p>
 * An answer that is absent altogether is not one of these: the question has simply not been asked
 * yet, and the caller asks it.
 */
public sealed interface WindowAnswer {

    /**
     * The user chose a window, with what the form gave it; it is resolved by the same rules as a
     * window a call names.
     *
     * @param window the window chosen
     * @param given  the form's fields that window takes
     */
    record Chosen(DownloadWindow window, WindowArguments given) implements WindowAnswer {

        public Chosen {
            if (window == null || given == null) {
                throw new IllegalArgumentException("A chosen answer needs its window and arguments");
            }
        }
    }

    /**
     * The user declined or dismissed the question: nothing is downloaded.
     *
     * @param reason what the user did, in a sentence the caller can pass on
     */
    record NotAnswered(String reason) implements WindowAnswer {

        public NotAnswered {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("An unanswered question needs the reason it went unanswered");
            }
        }
    }

    /**
     * The user answered, but what came back does not make a window. The question is asked again,
     * stating this problem first.
     *
     * @param problem what is missing or wrong, in a sentence the user can act on
     */
    record Incomplete(String problem) implements WindowAnswer {

        public Incomplete {
            if (problem == null || problem.isBlank()) {
                throw new IllegalArgumentException("An incomplete answer needs the problem stated");
            }
        }
    }
}
