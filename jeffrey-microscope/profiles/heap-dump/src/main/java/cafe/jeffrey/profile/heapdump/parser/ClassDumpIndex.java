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

import org.eclipse.collections.api.map.primitive.LongObjectMap;

import java.util.List;

/**
 * Output of Pass A ({@link HprofClassDumpWalker}). Read-only after
 * construction; passed by reference to Pass B (which uses {@link #byId}
 * read-only), the shallow-size corrector, the string-content writer, and the
 * metadata writer.
 *
 * <p>{@code byId} is keyed on the primitive {@code long} class id via the
 * Eclipse Collections {@link LongObjectMap}, so the hot {@code byId().get(id)}
 * call in {@code HprofPassBWalker#emitInstanceRefs} never boxes the id into a
 * {@code java.lang.Long}.
 */
public record ClassDumpIndex(
        LongObjectMap<HprofRecord.ClassDump> byId,
        long classCount,
        List<ParseWarning> warnings) {
}
