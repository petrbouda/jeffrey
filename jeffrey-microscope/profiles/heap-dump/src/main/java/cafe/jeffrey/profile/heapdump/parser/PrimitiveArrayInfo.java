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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * In-memory metadata the indexer needs to decode every {@code java.lang.String}'s
 * backing primitive array: the triplet that the string-content writer would
 * otherwise read with a SQL PK lookup. Populated by Pass B (one entry per
 * {@code PRIMITIVE_ARRAY_DUMP}), consumed by the string-content phase. Held
 * only during the index build.
 */
public record PrimitiveArrayInfo(long fileOffset, int arrayLength, int elementType) {
}
