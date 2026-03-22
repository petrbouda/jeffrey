/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.profile.writer;

import pbouda.jeffrey.provider.profile.model.writer.EventThreadWithHash;

import java.util.*;

class EventThreadCleaner {

    public List<EventThreadWithHash> clean(List<EventThreadWithHash> threads) {
        Map<Long, List<EventThreadWithHash>> threadsByOsId = collectThreadsByOsId(threads);
        threadsByOsId = fixUnknownThreadNames(threadsByOsId);
        return threadsByOsId.values().stream()
                .flatMap(Collection::stream)
                .toList();
    }

    private static Map<Long, List<EventThreadWithHash>> collectThreadsByOsId(List<EventThreadWithHash> threads) {
        Map<Long, List<EventThreadWithHash>> threadsByOsId = new HashMap<>();
        for (EventThreadWithHash thread : threads) {
            threadsByOsId.compute(thread.eventThread().osId(), (osId, tlist) -> {
                if (tlist == null) {
                    tlist = new ArrayList<>();
                }
                tlist.add(thread);
                return tlist;
            });
        }
        return threadsByOsId;
    }

    /**
     * Some threads don't have a name, this method fixes the name (usually GC and JIT threads with
     * the name of [tid=25432]) and tries to find a thread with the same `os_id` to copy the real name.
     *
     * @param threadsByOsId list of all threads grouped by `os_id`
     * @return map of threads with fixed unknown names
     */
    private static Map<Long, List<EventThreadWithHash>> fixUnknownThreadNames(Map<Long, List<EventThreadWithHash>> threadsByOsId) {
        Map<Long, List<EventThreadWithHash>> modified = new HashMap<>(threadsByOsId);
        for (Map.Entry<Long, List<EventThreadWithHash>> threadsEntry : threadsByOsId.entrySet()) {
            List<EventThreadWithHash> threads = threadsEntry.getValue();

            // Find the longest name of the threads that is not an "unknown" name
            // this kind of name will be used for the entire group of threads
            String longestName = threads.stream()
                    .map(t -> t.eventThread().name())
                    .filter(name -> !name.startsWith("[tid="))
                    .max(Comparator.comparingInt(String::length))
                    .orElse(null);

            // Use the longest name for the entire group of threads
            // otherwise, keep the original names (unknown names)
            if (longestName != null) {
                List<EventThreadWithHash> modifiedThreads = threads.stream()
                        .map(t -> new EventThreadWithHash(t.hash(), t.eventThread().withName(longestName)))
                        .toList();

                modified.put(threadsEntry.getKey(), modifiedThreads);
            } else {
                modified.put(threadsEntry.getKey(), threads);
            }
        }

        return modified;
    }
}
