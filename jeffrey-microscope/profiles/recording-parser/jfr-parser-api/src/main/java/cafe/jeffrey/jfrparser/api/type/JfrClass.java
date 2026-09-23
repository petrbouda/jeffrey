/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
package cafe.jeffrey.jfrparser.api.type;

public interface JfrClass {
    /**
     * The class name with any hidden-class address stripped off, so it is stable across runs.
     */
    String className();

    /**
     * The per-run identity a hidden class carries in its name (e.g. {@code 0x0000000011cb1be8}),
     * or {@code null} when the class is not hidden. Only the ingest paths that parse a recording
     * know this, so it defaults to "not hidden" and stays a single-abstract-method interface.
     */
    default String hiddenClassId() {
        return null;
    }

    default boolean isHidden() {
        return hiddenClassId() != null;
    }
}
