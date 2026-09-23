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
 * One row of the {@code class} table.
 *
 * Nullable fields ({@code superClassId}, {@code classloaderId}, {@code signersId},
 * {@code protectionDomainId}) are {@code null} when the corresponding HPROF id
 * is 0 ("no reference") — the index translates 0 to NULL on write so callers
 * don't have to special-case the convention.
 */
public record JavaClassRow(
        long classId,
        int classSerial,
        String name,
        boolean isArray,
        Long superClassId,
        Long classloaderId,
        Long signersId,
        Long protectionDomainId,
        int instanceSize,
        int staticFieldsSize,
        long fileOffset) {

    public JavaClassRow {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }
}
