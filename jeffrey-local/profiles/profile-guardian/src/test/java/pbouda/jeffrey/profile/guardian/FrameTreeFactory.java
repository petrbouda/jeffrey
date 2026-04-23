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

package pbouda.jeffrey.profile.guardian;

import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.profile.common.model.FrameType;

/**
 * Builds synthetic {@link Frame} trees for unit tests.
 * <p>
 * The real tree builder ({@code JfrRecordingEventParser}) calls {@link Frame#increment} once per
 * stack trace with {@code isTopFrame=true} only for the leaf. To construct a frame with a known
 * {@code totalSamples} / {@code selfSamples} split here, the factory calls {@code increment} twice
 * (once as passthrough, once as leaf) so the internal counters match what the real parser would
 * produce for equivalent stacks.
 */
public final class FrameTreeFactory {

    private FrameTreeFactory() {
    }

    /**
     * Creates a frame that has {@code totalSamples} samples passing through it, of which
     * {@code selfSamples} terminate at this frame (i.e. were the leaf of their stack trace).
     */
    public static Frame node(String methodName, long totalSamples, long selfSamples) {
        if (selfSamples > totalSamples) {
            throw new IllegalArgumentException("selfSamples cannot exceed totalSamples");
        }
        Frame frame = new Frame(null, methodName, 0, 0);
        long passthrough = totalSamples - selfSamples;
        if (passthrough > 0) {
            frame.increment(FrameType.JIT_COMPILED, passthrough, passthrough, false);
        }
        if (selfSamples > 0) {
            frame.increment(FrameType.JIT_COMPILED, selfSamples, selfSamples, true);
        }
        return frame;
    }

    /** Attaches children to a parent frame and returns the parent, for fluent tree construction. */
    public static Frame withChildren(Frame parent, Frame... children) {
        for (Frame child : children) {
            parent.put(child.methodName(), child);
        }
        return parent;
    }
}
