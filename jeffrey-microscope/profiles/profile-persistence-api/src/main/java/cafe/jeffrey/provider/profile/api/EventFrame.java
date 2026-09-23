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

package cafe.jeffrey.provider.profile.api;

/**
 * One stack frame on its way into the profile database.
 *
 * @param clazz          class name with any hidden-class address stripped off, so it stays stable
 *                       across runs
 * @param method         method name
 * @param type           raw frame type code (e.g. "Interpreted", "JIT compiled", "Inlined")
 * @param bci            bytecode index
 * @param line           line number
 * @param hiddenClassId  per-run identity of a hidden class (e.g. {@code 0x0000000011cb1be8}),
 *                       {@code null} for ordinary classes
 */
public record EventFrame(String clazz, String method, String type, long bci, long line, String hiddenClassId) {

    /**
     * A frame on an ordinary (non-hidden) class.
     */
    public EventFrame(String clazz, String method, String type, long bci, long line) {
        this(clazz, method, type, bci, line, null);
    }
}
