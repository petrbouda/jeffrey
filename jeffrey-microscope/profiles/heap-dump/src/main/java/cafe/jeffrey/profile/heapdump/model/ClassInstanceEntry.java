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

package cafe.jeffrey.profile.heapdump.model;

import java.util.Map;

/**
 * A single instance entry when browsing instances of a class.
 *
 * @param objectId       unique object identifier
 * @param shallowSize    shallow size in bytes
 * @param retainedSize   retained size in bytes (null if not computed)
 * @param objectParams   structured key/value pairs describing the instance
 * @param contentPreview short human-readable rendering of the instance's content
 *                       (e.g. the decoded String text, a boxed primitive value,
 *                       a Thread/Class/Enum name) — {@code null} when the class
 *                       has no known preview layout
 * @param referrerClass  class name of the dominant referrer for this instance,
 *                       or {@code null} when the class is self-describing /
 *                       has no known referrer hint; today only populated for
 *                       {@code byte[]} rows
 */
public record ClassInstanceEntry(
        long objectId,
        long shallowSize,
        Long retainedSize,
        Map<String, String> objectParams,
        String contentPreview,
        String referrerClass
) {
}
