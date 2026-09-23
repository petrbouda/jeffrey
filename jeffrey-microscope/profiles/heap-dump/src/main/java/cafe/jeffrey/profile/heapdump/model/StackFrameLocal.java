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

package cafe.jeffrey.profile.heapdump.model;

/**
 * A local variable or reference on a thread's stack frame.
 *
 * @param objectId    the object ID of the referenced instance
 * @param className   the class name of the referenced instance
 * @param fieldName   the name of the local variable (if available)
 * @param shallowSize the shallow size of the referenced instance in bytes
 */
public record StackFrameLocal(
        long objectId,
        String className,
        String fieldName,
        long shallowSize
) {
}
