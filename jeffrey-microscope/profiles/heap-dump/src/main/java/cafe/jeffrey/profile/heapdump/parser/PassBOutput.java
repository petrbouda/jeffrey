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
 * Output of Pass B (fused parallel walk of instances / roots / refs). The
 * primitive-array-info map is what the string-content writer subsequently
 * joins on to decode {@code java.lang.String} backing arrays.
 *
 * <p>{@code primArrInfo} is keyed on the primitive {@code long} array id so the
 * hot per-String lookup in {@code HprofStringContentWriter#runWorker} never
 * boxes the id.
 */
public record PassBOutput(
        long instanceCount,
        long gcRootCount,
        long outboundRefCount,
        long subRecordCount,
        LongObjectMap<PrimitiveArrayInfo> primArrInfo,
        List<ParseWarning> warnings) {
}
