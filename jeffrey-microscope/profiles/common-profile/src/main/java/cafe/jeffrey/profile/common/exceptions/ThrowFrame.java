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

/**
 * One frame of a throw's stack.
 *
 * @param className  the frame's class; null for a native frame the recording could not attribute
 * @param methodName the frame's method; null when unknown
 * @param lineNumber the source line; null when the recording carries none
 */
public record ThrowFrame(String className, String methodName, Integer lineNumber) {

    /**
     * Whether this frame belongs to any of the given class-name prefixes.
     */
    public boolean startsWithAny(Iterable<String> prefixes) {
        if (className == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
