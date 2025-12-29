/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.api.model;

import pbouda.jeffrey.shared.model.StacktraceTag;
import pbouda.jeffrey.shared.model.StacktraceType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record EventStacktrace(StacktraceType type, List<EventFrame> frames, Set<StacktraceTag> tags) {

    public EventStacktrace(StacktraceType type, List<EventFrame> frames) {
        this(type, frames, new HashSet<>());
    }

    public void addStacktraceTags(Set<StacktraceTag> tags) {
        this.tags.addAll(tags);
    }
}
