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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * One slice of a profile's timeline, a lane per group of identically named threads.
 *
 * @param common        the metadata every lane is drawn against
 * @param groups        the lanes in this slice, already ordered
 * @param offset        how many groups precede this slice
 * @param matchedGroups groups matching the current filter — what "showing 4 of 18" counts
 * @param totalGroups   groups in the recording, filtered or not
 * @param totalThreads  threads behind those groups, which is the number the lanes stand in for
 */
public record ThreadGroupPage(
        ThreadCommon common,
        List<ThreadGroup> groups,
        int offset,
        int matchedGroups,
        int totalGroups,
        int totalThreads) {

    public boolean hasMore() {
        return offset + groups.size() < matchedGroups;
    }
}
