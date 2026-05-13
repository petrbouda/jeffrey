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
