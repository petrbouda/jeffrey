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

package cafe.jeffrey.shared.volume.layout;

import java.util.Optional;

/**
 * Which of an instance's sessions one read takes, newest first: at most {@code limit} of them,
 * starting after the session {@code before} names — the cursor the previous page ended on.
 *
 * @param limit  how many sessions at most; {@link Integer#MAX_VALUE} for all of them
 * @param before the session the page starts after, or empty for the newest
 */
public record SessionPage(int limit, Optional<String> before) {

    /** Every session of the instance. */
    public static final SessionPage ALL = new SessionPage(Integer.MAX_VALUE, Optional.empty());

    public SessionPage {
        if (limit < 1 || before == null) {
            throw new IllegalArgumentException("A session page takes at least one session and a cursor, if only an empty one");
        }
    }
}
