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

package cafe.jeffrey.profile.manager.model.io;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.model.Type;

/**
 * Scopes an I/O event stream to a single endpoint — a socket peer ({@code host:port}) or a file
 * path — or lets every event through. Used to plot one peer's throughput next to the aggregate.
 *
 * @param target the endpoint to keep, or {@code null} to keep everything
 */
public record IoTargetFilter(String target) {

    private static final IoTargetFilter ALL = new IoTargetFilter(null);

    /**
     * Keeps every event of the stream.
     */
    public static IoTargetFilter all() {
        return ALL;
    }

    /**
     * Keeps only the events of the given endpoint; a blank or {@code null} target keeps everything,
     * so an omitted request parameter degrades to the aggregate view.
     */
    public static IoTargetFilter ofNullable(String target) {
        return target == null || target.isBlank() ? ALL : new IoTargetFilter(target);
    }

    boolean matches(Type type, ObjectNode fields) {
        return target == null || target.equals(IoEventFields.target(type, fields));
    }
}
