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

package cafe.jeffrey.microscope.remote.client;

import java.util.Optional;

/**
 * Which of an instance's sessions one read names, newest first: one listing of the instance names
 * them all, so a page costs that listing whatever its size, and no session on it is read.
 *
 * @param limit  how many sessions at most; {@link Integer#MAX_VALUE} for all of them
 * @param before the session the page starts after, or empty for the newest
 */
public record InstancePage(int limit, Optional<String> before) {

    /** How many sessions the instance's page shows before "Show older sessions". */
    public static final int PAGE_SIZE = 10;

    public InstancePage {
        if (limit < 1 || before == null) {
            throw new IllegalArgumentException("An instance page takes at least one session and a cursor, if only an empty one");
        }
    }

    /** Every session. */
    public static InstancePage whole() {
        return new InstancePage(Integer.MAX_VALUE, Optional.empty());
    }

    /** The newest {@link #PAGE_SIZE} sessions after {@code before}. */
    public static InstancePage newest(Optional<String> before) {
        return new InstancePage(PAGE_SIZE, before);
    }
}
