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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventThreadWithHash;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives a name to the threads the JVM recorded without one.
 * <p>
 * Some events name their thread {@code [tid=25432]} — typically a GC or JIT thread sampled before
 * the JVM had a Java name for it. Another event on the same OS thread usually does carry the real
 * name, so the two are matched on {@code os_id} and the placeholder is replaced.
 * <p>
 * Two threads are only the same thread if they share an OS thread, and only a placeholder name is
 * ever replaced. Both matter: a virtual thread has no {@code os_id} at all — treating that absence
 * as an id would make every virtual thread in the recording one thread — and a thread that already
 * has a name has nothing to gain from a neighbour's.
 */
class EventThreadCleaner {

    /** How the JVM spells "this thread had no Java name when the event was recorded". */
    private static final String UNKNOWN_NAME_PREFIX = "[tid=";

    public List<EventThreadWithHash> clean(List<EventThreadWithHash> threads) {
        Map<Long, String> namesByOsId = recordedNamesByOsId(threads);
        return threads.stream()
                .map(thread -> named(thread, namesByOsId))
                .toList();
    }

    /**
     * The real name each OS thread was seen under. Where an OS thread was seen under more than one
     * — a pool thread renamed as it is handed work — the longest wins, being the most specific.
     */
    private static Map<Long, String> recordedNamesByOsId(List<EventThreadWithHash> threads) {
        Map<Long, String> namesByOsId = new HashMap<>();
        for (EventThreadWithHash thread : threads) {
            Long osId = thread.eventThread().osId();
            String name = thread.eventThread().name();
            if (osId == null || isUnknown(name)) {
                continue;
            }
            namesByOsId.merge(osId, name, (kept, candidate) ->
                    candidate.length() > kept.length() ? candidate : kept);
        }
        return namesByOsId;
    }

    private static EventThreadWithHash named(EventThreadWithHash thread, Map<Long, String> namesByOsId) {
        EventThread eventThread = thread.eventThread();
        if (!isUnknown(eventThread.name()) || eventThread.osId() == null) {
            return thread;
        }
        String recorded = namesByOsId.get(eventThread.osId());
        if (recorded == null) {
            return thread;
        }
        return new EventThreadWithHash(thread.hash(), eventThread.withName(recorded));
    }

    private static boolean isUnknown(String name) {
        return name.startsWith(UNKNOWN_NAME_PREFIX);
    }
}
