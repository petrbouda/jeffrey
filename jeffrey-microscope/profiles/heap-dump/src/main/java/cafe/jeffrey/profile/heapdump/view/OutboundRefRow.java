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
package cafe.jeffrey.profile.heapdump.view;

/**
 * One row of the {@code outbound_ref} table — a single object-to-object
 * reference extracted during the index build.
 *
 * @param sourceId  the holder
 * @param targetId  the referenced object
 * @param fieldKind 0 = instance field, 1 = array element, 2 = class static
 * @param fieldId   field declaration index for instance/static refs;
 *                  array index for array refs
 */
public record OutboundRefRow(long sourceId, long targetId, int fieldKind, int fieldId) {
}
