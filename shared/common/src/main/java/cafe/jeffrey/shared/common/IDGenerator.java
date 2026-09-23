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

package cafe.jeffrey.shared.common;

import com.github.f4b6a3.uuid.UuidCreator;

public abstract class IDGenerator {

    /**
     * UUID generator described here:
     * <a href="https://github.com/f4b6a3/uuid-creator/wiki/1.7.-UUIDv7#type-2-plus-1">...</a>
     *
     * @return UUID represented as a string
     */
    public static String generate() {
        return UuidCreator.getTimeOrderedEpochPlus1().toString();
    }
}
