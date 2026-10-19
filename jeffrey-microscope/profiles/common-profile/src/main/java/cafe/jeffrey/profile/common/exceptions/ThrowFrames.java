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

package cafe.jeffrey.profile.common.exceptions;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a throw's stack actually starts — the first frame below the exception's constructor chain.
 * <p>
 * The same rule as {@code throwingFrameIndex} in the Microscope frontend
 * ({@code services/trace/traceStackFolding.ts}), which marks the frame in the trace drill-down; this
 * is the server's reading of it, so the Exceptions page and MCP name the same frame the stack view
 * highlights.
 * <p>
 * {@code jdk.JavaExceptionThrow} and {@code jdk.JavaErrorThrow} are emitted from inside
 * {@code Throwable}'s constructor, so every throw's stack opens with one {@code <init>} per level of
 * the exception's own hierarchy — {@code Throwable.<init>}, {@code Exception.<init>},
 * {@code IOException.<init>} — and only then the frame that contained the {@code throw}. The boundary
 * is found rather than guessed, because the thrown class names exactly the most-derived constructor
 * in that chain: a throw from <em>inside</em> a constructor leaves an {@code <init>} frame that is the
 * real answer, and a blanket "strip every leading {@code <init>}" would eat it.
 */
public final class ThrowFrames {

    /** Methods that only ever appear while an exception is building its own stack. */
    private static final Set<String> CONSTRUCTOR_CHAIN_METHODS = Set.of("<init>", "fillInStackTrace");

    private static final int NOT_FOUND = -1;

    private ThrowFrames() {
    }

    /**
     * @param topFirstFrames the throw's stack, the frame on top of it first
     * @param thrownClass    the class the event recorded as thrown
     * @return the index of the throwing frame, or 0 when the stack does not have this shape — a stack
     *         this rule cannot read is one it should not move the mark on
     */
    public static int throwingFrameIndex(List<ThrowFrame> topFirstFrames, String thrownClass) {
        if (thrownClass == null || topFirstFrames.isEmpty()) {
            return 0;
        }

        int chainEnd = NOT_FOUND;
        for (int index = 0; index < topFirstFrames.size(); index++) {
            ThrowFrame frame = topFirstFrames.get(index);
            if (frame.methodName() == null || !CONSTRUCTOR_CHAIN_METHODS.contains(frame.methodName())) {
                break;
            }
            if (thrownClass.equals(frame.className())) {
                // Not a break: a chain can name the same class more than once, and it is the last of
                // them that ends it.
                chainEnd = index;
            }
        }

        // The whole stack being the chain would leave no frame to mark, so the mark stays where it is.
        return chainEnd != NOT_FOUND && chainEnd + 1 < topFirstFrames.size() ? chainEnd + 1 : 0;
    }

    /**
     * The frame that contained the {@code throw}; empty only for an empty stack.
     */
    public static Optional<ThrowFrame> throwingFrame(List<ThrowFrame> topFirstFrames, String thrownClass) {
        if (topFirstFrames.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(topFirstFrames.get(throwingFrameIndex(topFirstFrames, thrownClass)));
    }
}
