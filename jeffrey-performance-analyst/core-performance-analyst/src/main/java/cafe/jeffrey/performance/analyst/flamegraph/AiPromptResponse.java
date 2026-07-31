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

package cafe.jeffrey.performance.analyst.flamegraph;

import java.util.List;

/**
 * A generated prompt as the browser sees it.
 *
 * <p>Deliberately not {@link FlamegraphAiPrompt} itself: that carries the full frame index, which can
 * run to thousands of entries and has no use in the UI. Sending it would make every prompt request
 * needlessly large to serve a field nothing reads.</p>
 */
public record AiPromptResponse(String eventType, String label, long samples, String markdown) {

    public static List<AiPromptResponse> from(List<FlamegraphAiPrompt> prompts) {
        return prompts.stream()
                .map(prompt -> new AiPromptResponse(
                        prompt.eventType(), prompt.label(), prompt.samples(), prompt.markdown()))
                .toList();
    }
}
