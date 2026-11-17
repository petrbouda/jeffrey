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

package cafe.jeffrey.profile.manager.model.thread.dump;

/**
 * Java thread state as reported by a {@code java.lang.Thread.State:} line in a thread dump. JVM/GC and
 * other native threads have no such line and map to {@link #UNKNOWN}.
 */
public enum ThreadState {
    RUNNABLE,
    BLOCKED,
    WAITING,
    TIMED_WAITING,
    NEW,
    TERMINATED,
    UNKNOWN;

    /**
     * Resolves the state from the token following {@code java.lang.Thread.State:} (e.g.
     * {@code "WAITING (parking)"} → {@link #WAITING}). Returns {@link #UNKNOWN} for anything unparseable.
     */
    public static ThreadState fromLabel(String label) {
        if (label == null) {
            return UNKNOWN;
        }
        String token = label.trim();
        int cut = token.indexOf(' ');
        if (cut > 0) {
            token = token.substring(0, cut);
        }
        try {
            return valueOf(token);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
