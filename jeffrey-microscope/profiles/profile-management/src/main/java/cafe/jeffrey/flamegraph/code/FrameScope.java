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

package cafe.jeffrey.flamegraph.code;

import java.util.List;

/**
 * Which frames count as the application's own code, decided by the reader who holds the repository
 * rather than guessed here: the package prefixes of the code they can open and change.
 * <p>
 * Everything below the deepest own frame of a stack -- a library, the JDK, the JVM -- is work that own
 * frame asked for, which is what lets a cost be charged to a method someone can edit.
 */
public sealed interface FrameScope {

    /** Whether a Java frame of this class is the application's own. */
    boolean isOwn(String className);

    /** Whether any frame can be own at all; without a scope nothing is charged. */
    boolean chargesCost();

    /**
     * The classes whose names start with one of the prefixes. A prefix is matched as given, so
     * {@code com.acme.} keeps {@code com.acmegroup} out where {@code com.acme} would let it in.
     *
     * @param prefixes the package or class prefixes, at least one, none blank
     */
    record OwnCode(List<String> prefixes) implements FrameScope {

        public OwnCode {
            if (prefixes == null || prefixes.isEmpty()) {
                throw new IllegalArgumentException("Own code needs at least one package prefix");
            }
            prefixes = prefixes.stream()
                    .map(prefix -> prefix == null ? "" : prefix.strip())
                    .distinct()
                    .toList();
            if (prefixes.stream().anyMatch(String::isEmpty)) {
                throw new IllegalArgumentException("A package prefix must not be blank");
            }
        }

        @Override
        public boolean isOwn(String className) {
            for (String prefix : prefixes) {
                if (className.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean chargesCost() {
            return true;
        }
    }

    /** No own code named: costs are read per method, nothing is charged to a caller. */
    record Everything() implements FrameScope {

        @Override
        public boolean isOwn(String className) {
            return false;
        }

        @Override
        public boolean chargesCost() {
            return false;
        }
    }

    /** The scope for these prefixes, or {@link Everything} when none is given. */
    static FrameScope of(List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return new Everything();
        }
        return new OwnCode(prefixes);
    }
}
