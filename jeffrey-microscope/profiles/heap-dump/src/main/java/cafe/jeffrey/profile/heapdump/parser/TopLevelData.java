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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable accumulator written during {@link HprofTopLevelWalk#walk}; treated
 * as read-only by every downstream phase (stack-trace writer, Pass A, Pass B,
 * string-content writer, metadata writer).
 *
 * <p>STRING records stream straight through to the {@code string} table — the
 * pool here only holds the raw UTF-8 bytes that later phases need to resolve
 * class / field / method names. LoadClass and HeapDumpRegion records are
 * buffered in full because Pass A and Pass B revisit them; STACK_FRAME /
 * STACK_TRACE entries are buffered so the stack-trace writer can resolve
 * string ids after the entire pool has loaded (HPROF doesn't guarantee STRING
 * records come first).
 */
public final class TopLevelData {

    public final Map<Long, byte[]> stringPool = new HashMap<>();

    public final Map<Long, HprofRecord.LoadClass> loadClassByClassId = new HashMap<>();

    public final List<HprofRecord.HeapDumpRegion> regions = new ArrayList<>();

    public final List<HprofRecord.StackFrame> stackFrames = new ArrayList<>();

    public final List<HprofRecord.StackTrace> stackTraces = new ArrayList<>();

    public final List<ParseWarning> warnings = new ArrayList<>();

    public long stringCount;

    public long recordCount;
}
