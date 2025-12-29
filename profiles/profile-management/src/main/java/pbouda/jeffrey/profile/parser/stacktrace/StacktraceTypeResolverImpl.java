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

package pbouda.jeffrey.profile.parser.stacktrace;

import pbouda.jeffrey.shared.model.StacktraceType;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.provider.api.model.EventFrame;
import pbouda.jeffrey.provider.api.model.EventThread;

import java.util.List;

public class StacktraceTypeResolverImpl implements StacktraceTypeResolver {

    private static final List<Type> OVERALL_TYPES = List.of(
            Type.EXECUTION_SAMPLE, Type.WALL_CLOCK_SAMPLE, Type.MALLOC, Type.FREE);

    private StacktraceTypeResolver innerResolver;

    @Override
    public void start(Type type) {
        if (OVERALL_TYPES.contains(type)) {
            this.innerResolver = new OverallStacktraceTypeResolver();
        } else {
            this.innerResolver = AlwaysApplicationStacktraceTypeResolver.INSTANCE;
        }
        this.innerResolver.start(type);
    }

    @Override
    public void applyThread(EventThread thread) {
        innerResolver.applyThread(thread);
    }

    @Override
    public void applyFrame(EventFrame frame) {
        innerResolver.applyFrame(frame);
    }

    @Override
    public StacktraceType resolve() {
        return innerResolver.resolve();
    }
}
