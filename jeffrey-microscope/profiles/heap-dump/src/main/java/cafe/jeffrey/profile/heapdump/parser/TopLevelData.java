/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
