/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.thread;

import cafe.jeffrey.shared.common.model.Type;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a thread was doing during a period on the timeline. Each state maps 1:1 to the JFR event type
 * it is reconstructed from, which lets both the timeline query and the per-band event lookup
 * translate between the two without a hand-rolled conditional ladder.
 */
public enum ThreadState {
    STARTED,
    ENDED,
    WAITING,
    BLOCKED,
    PARKED,
    SLEEP,
    SOCKET_READ,
    SOCKET_WRITE,
    FILE_READ,
    FILE_WRITE;

    private static final Map<Type, ThreadState> BY_EVENT_TYPE = Map.of(
            Type.THREAD_START, STARTED,
            Type.THREAD_END, ENDED,
            Type.THREAD_PARK, PARKED,
            Type.THREAD_SLEEP, SLEEP,
            Type.JAVA_MONITOR_ENTER, BLOCKED,
            Type.JAVA_MONITOR_WAIT, WAITING,
            Type.SOCKET_READ, SOCKET_READ,
            Type.SOCKET_WRITE, SOCKET_WRITE,
            Type.FILE_READ, FILE_READ,
            Type.FILE_WRITE, FILE_WRITE);

    private static final Map<ThreadState, Type> EVENT_TYPE_BY_STATE = BY_EVENT_TYPE.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    /**
     * The states whose bands can be expanded into individual events (duration, monitor class, file
     * path, …). The lifespan states are excluded: they are reconstructed from start/end pairs rather
     * than read off a single event, so there is nothing to look up for them.
     */
    private static final Set<ThreadState> WITH_EVENT_DETAIL = EnumSet.complementOf(EnumSet.of(STARTED, ENDED));

    public static ThreadState fromEventType(Type eventType) {
        ThreadState state = BY_EVENT_TYPE.get(eventType);
        if (state == null) {
            throw new IllegalArgumentException("Event type has no thread state: " + eventType);
        }
        return state;
    }

    /**
     * The JFR event type this state is reconstructed from.
     */
    public Type eventType() {
        return EVENT_TYPE_BY_STATE.get(this);
    }

    /**
     * Whether the individual events behind a band of this state can be looked up for a tooltip.
     */
    public boolean hasEventDetail() {
        return WITH_EVENT_DETAIL.contains(this);
    }
}
