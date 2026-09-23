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

package cafe.jeffrey.profile.parser.stacktrace;

import cafe.jeffrey.microscope.model.StacktraceType;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.EventFrame;
import cafe.jeffrey.provider.profile.api.EventThread;

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
