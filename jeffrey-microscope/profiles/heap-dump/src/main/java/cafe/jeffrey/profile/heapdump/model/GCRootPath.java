/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import java.util.List;

/**
 * Represents a reference chain from a GC root to a target object.
 *
 * @param rootObjectId  unique ID of the GC root object
 * @param rootClassName class name of the GC root object
 * @param rootType      type of GC root (e.g. "Java Frame", "JNI Global")
 * @param threadName    thread name for Java Frame roots, null otherwise
 * @param stackFrame    stack frame info (e.g. "MyClass.method(MyClass.java:42)") for Java Frame roots, null otherwise
 * @param steps         ordered chain of references from root to target
 */
public record GCRootPath(
        long rootObjectId,
        String rootClassName,
        String rootType,
        String threadName,
        String stackFrame,
        List<PathStep> steps
) {
}
