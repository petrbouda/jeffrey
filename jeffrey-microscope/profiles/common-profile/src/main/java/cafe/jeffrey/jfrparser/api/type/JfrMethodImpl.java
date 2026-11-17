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

package cafe.jeffrey.jfrparser.api.type;

/**
 * Plain in-memory {@link JfrMethod}. Shared by every parser path (DB row mappers, in-memory JFR
 * parsing) so a class/method pair has a single canonical carrier.
 */
public record JfrMethodImpl(String className, String methodName, String hiddenClassId)
        implements JfrMethod, JfrClass {

    /**
     * A method on an ordinary (non-hidden) class.
     */
    public JfrMethodImpl(String className, String methodName) {
        this(className, methodName, null);
    }

    /**
     * Parses an entity of the form {@code Class#method} (or just {@code Class}) into a method.
     */
    public static JfrMethod of(String entity) {
        if (entity == null || entity.isBlank()) {
            return null;
        }

        String[] split = entity.split("#");
        if (split.length == 0) {
            return null;
        }

        if (split.length == 2) {
            return new JfrMethodImpl(split[0], split[1]);
        } else {
            return new JfrMethodImpl(split[0], null);
        }
    }

    public static JfrClass ofClass(String className) {
        return new JfrMethodImpl(className, null);
    }

    @Override
    public JfrClass clazz() {
        return this;
    }
}
