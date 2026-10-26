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
 * Which of an instance's sessions one read asks for, newest first, and whether each comes with its
 * files. The instance's page asks for {@link #newest} pages with their files: the remote lists each
 * session's directory to count its files anyway, so sending them costs no further call, and opening
 * a session on the page then asks for nothing.
 *
 * @param limit     how many sessions at most; {@link Integer#MAX_VALUE} for all of them
 * @param before    the session the page starts after, or empty for the newest
 * @param withFiles whether each session carries its files, not only their count and size
 */
public record InstancePage(int limit, Optional<String> before, boolean withFiles) {

    /** How many sessions the instance's page shows before "Show older sessions". */
    public static final int PAGE_SIZE = 10;

    public InstancePage {
        if (limit < 1 || before == null) {
            throw new IllegalArgumentException("An instance page takes at least one session and a cursor, if only an empty one");
        }
    }

    /** Every session, each with its count and size but not its files. */
    public static InstancePage whole() {
        return new InstancePage(Integer.MAX_VALUE, Optional.empty(), false);
    }

    /** The newest {@link #PAGE_SIZE} sessions after {@code before}, each with its files. */
    public static InstancePage newest(Optional<String> before) {
        return new InstancePage(PAGE_SIZE, before, true);
    }
}
