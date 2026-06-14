/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
