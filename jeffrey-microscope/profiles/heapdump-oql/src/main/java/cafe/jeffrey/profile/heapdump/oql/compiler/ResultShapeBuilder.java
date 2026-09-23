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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper used by {@code SqlEmitter} to track which projection column
 * carries which distinguished role (instance id, class name, sizes).
 */
final class ResultShapeBuilder {

    private final List<String> columns = new ArrayList<>();
    private int objectId = -1;
    private int className = -1;
    private int shallowSize = -1;
    private int retainedSize = -1;

    void add(String name, boolean isObjectId, boolean isClassName, boolean isShallowSize, boolean isRetainedSize) {
        int idx = columns.size();
        columns.add(name);
        if (isObjectId && objectId == -1) {
            objectId = idx;
        }
        if (isClassName && className == -1) {
            className = idx;
        }
        if (isShallowSize && shallowSize == -1) {
            shallowSize = idx;
        }
        if (isRetainedSize && retainedSize == -1) {
            retainedSize = idx;
        }
    }

    ResultShape build() {
        return new ResultShape(objectId, className, shallowSize, retainedSize, columns);
    }
}
