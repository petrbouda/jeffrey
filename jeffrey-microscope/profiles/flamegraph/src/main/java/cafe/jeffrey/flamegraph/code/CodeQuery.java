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

package cafe.jeffrey.flamegraph.code;

import java.util.List;

/**
 * What a code read is asked: whose code is own, and which methods to describe in full.
 *
 * @param scope the reader's own code, or {@link FrameScope.Everything}
 * @param focus the methods to describe, in the order the answer should hold them; may be empty
 */
public record CodeQuery(FrameScope scope, List<MethodRef> focus) {

    public CodeQuery {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        focus = focus == null ? List.of() : List.copyOf(focus);
    }

    /** Own code by these prefixes, or none, and these methods in focus. */
    public static CodeQuery of(List<String> framePrefixes, List<MethodRef> focus) {
        return new CodeQuery(FrameScope.of(framePrefixes), focus);
    }
}
