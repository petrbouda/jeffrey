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

/**
 * One generated AI flamegraph prompt for a recording, per sample event type: the tab label, the total
 * sample count (for the chip), the prompt markdown the model reads, and the same call tree in the
 * machine-readable form Jeffrey checks the model's answer against.
 *
 * <p>The frame index is not sent to the browser — it would be large and the UI has no use for it — so
 * the controller maps this to a response type that omits it.</p>
 */
public record FlamegraphAiPrompt(
        String eventType,
        String label,
        long samples,
        String markdown,
        ProfileFrameIndex frameIndex) {

    public FlamegraphAiPrompt {
        frameIndex = frameIndex == null ? ProfileFrameIndex.empty() : frameIndex;
    }
}
