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

package cafe.jeffrey.jfrparser.jdk;

import cafe.jeffrey.microscope.model.Type;

import java.util.Collection;
import java.util.List;

public class ProcessableEvents {

    private final boolean processableAll;

    private final Collection<Type> events;

    public ProcessableEvents(boolean processableAll) {
        this(processableAll, List.of());
    }

    public static ProcessableEvents all() {
        return new ProcessableEvents(true);
    }

    public static ProcessableEvents of(Type event) {
        return new ProcessableEvents(false, List.of(event));
    }

    public static ProcessableEvents of(Collection<Type> events) {
        return new ProcessableEvents(false, events);
    }

    private ProcessableEvents(boolean processableAll, Collection<Type> events) {
        this.processableAll = processableAll;
        this.events = events;
    }

    public Collection<Type> events() {
        return events;
    }

    public boolean isProcessableAll() {
        return processableAll;
    }
}
