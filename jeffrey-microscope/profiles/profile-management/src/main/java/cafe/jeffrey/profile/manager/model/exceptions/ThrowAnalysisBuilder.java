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

package cafe.jeffrey.profile.manager.model.exceptions;

import cafe.jeffrey.profile.common.exceptions.JvmInternalThrows;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowFrames;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;
import cafe.jeffrey.profile.manager.TraceIds;
import cafe.jeffrey.provider.profile.api.ThrowCountRecord;
import cafe.jeffrey.provider.profile.api.ThrowMessageRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Turns the repository's throw counts, messages and stacks into a {@link ThrowAnalysis}.
 * <p>
 * Every (class, stack) pair is read once: its throwing frame is the first frame below the
 * constructor chain ({@link ThrowFrames}), and its origin follows from the class and that frame
 * ({@link JvmInternalThrows}). Counts, threads and messages then fold into two views — per class and
 * per site — from the same rows, so the two always add up to the same totals. Threads are kept as
 * sets until the end, because a distinct count cannot be summed across stacks.
 */
public final class ThrowAnalysisBuilder {

    /**
     * Distinct messages kept per row. A message that embeds an id or a timestamp makes every throw
     * distinct; past this many the list says nothing a reader can scan, and the omitted count says
     * how many more there are.
     */
    public static final int MESSAGES_LIMIT = 100;

    private static final Comparator<ExceptionTypeStat> TYPES_ORDER =
            Comparator.comparingLong(ExceptionTypeStat::count).reversed()
                    .thenComparing(ExceptionTypeStat::thrownClass);

    private static final Comparator<ExceptionSiteStat> SITES_ORDER =
            Comparator.comparingLong(ExceptionSiteStat::count).reversed()
                    .thenComparing(ExceptionSiteStat::thrownClass);

    private static final Comparator<ExceptionMessageCount> MESSAGES_ORDER =
            Comparator.comparingLong(ExceptionMessageCount::count).reversed()
                    .thenComparing(ExceptionMessageCount::message);

    /** What one (class, stack) pair reads as. */
    private record StackReading(ThrowFrame throwingFrame, ThrowOrigin origin) {
    }

    private record StackKey(String thrownClass, Long stacktraceHash) {
    }

    /** Two stacks reaching the same {@code throw} are one site. */
    private record SiteKey(String thrownClass, ThrowFrame throwingFrame) {
    }

    private static final class Accumulator {
        private long count;
        private boolean error;
        private final Set<Long> threads = new HashSet<>();
        private final Map<String, Long> messages = new HashMap<>();

        private void add(ThrowCountRecord record) {
            count += record.count();
            error |= record.error();
            if (record.threadHash() != null) {
                threads.add(record.threadHash());
            }
        }

        private void addMessage(ThrowMessageRecord record) {
            messages.merge(record.message(), record.count(), Long::sum);
        }
    }

    private static final class TypeAccumulator {
        private final Accumulator total = new Accumulator();
        private long applicationCount;
        private long jvmInternalCount;
    }

    private static final class SiteAccumulator {
        private final Accumulator total = new Accumulator();
        private final ThrowOrigin origin;
        private final Map<Long, Long> countsByStack = new HashMap<>();

        private SiteAccumulator(ThrowOrigin origin) {
            this.origin = origin;
        }
    }

    private final Map<Long, List<ThrowFrame>> stacks;
    private final Map<StackKey, StackReading> readings = new HashMap<>();
    private final Map<String, TypeAccumulator> types = new LinkedHashMap<>();
    private final Map<SiteKey, SiteAccumulator> sites = new LinkedHashMap<>();

    private ThrowAnalysisBuilder(Map<Long, List<ThrowFrame>> stacks) {
        this.stacks = stacks;
    }

    /**
     * @param counts   throw counts by class, stack and thread
     * @param messages throw counts by class, stack and message
     * @param stacks   the throws' stacks by hash, top frame first
     */
    public static ThrowAnalysis build(
            List<ThrowCountRecord> counts,
            List<ThrowMessageRecord> messages,
            Map<Long, List<ThrowFrame>> stacks) {

        if (counts.isEmpty()) {
            return ThrowAnalysis.EMPTY;
        }

        ThrowAnalysisBuilder builder = new ThrowAnalysisBuilder(stacks);
        counts.forEach(builder::addCount);
        messages.forEach(builder::addMessage);
        return builder.result();
    }

    private void addCount(ThrowCountRecord record) {
        StackReading reading = readingOf(record.thrownClass(), record.stacktraceHash());

        TypeAccumulator type = types.computeIfAbsent(record.thrownClass(), _ -> new TypeAccumulator());
        type.total.add(record);
        if (reading.origin() == ThrowOrigin.JVM_INTERNAL) {
            type.jvmInternalCount += record.count();
        } else {
            type.applicationCount += record.count();
        }

        SiteAccumulator site = sites.computeIfAbsent(
                new SiteKey(record.thrownClass(), reading.throwingFrame()),
                _ -> new SiteAccumulator(reading.origin()));
        site.total.add(record);
        if (record.stacktraceHash() != null) {
            site.countsByStack.merge(record.stacktraceHash(), record.count(), Long::sum);
        }
    }

    private void addMessage(ThrowMessageRecord record) {
        TypeAccumulator type = types.get(record.thrownClass());
        if (type != null) {
            type.total.addMessage(record);
        }
        StackReading reading = readingOf(record.thrownClass(), record.stacktraceHash());
        SiteAccumulator site = sites.get(new SiteKey(record.thrownClass(), reading.throwingFrame()));
        if (site != null) {
            site.total.addMessage(record);
        }
    }

    private StackReading readingOf(String thrownClass, Long stacktraceHash) {
        return readings.computeIfAbsent(new StackKey(thrownClass, stacktraceHash), _ -> {
            List<ThrowFrame> frames = stacktraceHash == null
                    ? List.of()
                    : stacks.getOrDefault(stacktraceHash, List.of());
            ThrowFrame throwingFrame = ThrowFrames.throwingFrame(frames, thrownClass).orElse(null);
            return new StackReading(throwingFrame, JvmInternalThrows.classify(thrownClass, throwingFrame));
        });
    }

    private ThrowAnalysis result() {
        List<ExceptionTypeStat> typeStats = new ArrayList<>(types.size());
        types.forEach((thrownClass, type) -> {
            List<ExceptionMessageCount> sorted = sortedMessages(type.total.messages);
            typeStats.add(new ExceptionTypeStat(
                    thrownClass,
                    type.total.count,
                    type.total.error,
                    capped(sorted),
                    omitted(sorted),
                    type.total.threads.size(),
                    type.applicationCount,
                    type.jvmInternalCount));
        });
        typeStats.sort(TYPES_ORDER);

        List<ExceptionSiteStat> siteStats = new ArrayList<>(sites.size());
        Map<String, List<ThrowFrame>> siteStacks = new HashMap<>();
        sites.forEach((key, site) -> {
            Long representative = commonestStack(site.countsByStack);
            String stacktraceId = representative == null ? null : TraceIds.hex(representative);
            if (stacktraceId != null) {
                siteStacks.put(stacktraceId, stacks.getOrDefault(representative, List.of()));
            }
            List<ExceptionMessageCount> sorted = sortedMessages(site.total.messages);
            siteStats.add(new ExceptionSiteStat(
                    key.thrownClass(),
                    site.total.error,
                    site.origin,
                    key.throwingFrame(),
                    stacktraceId,
                    site.total.count,
                    site.total.threads.size(),
                    capped(sorted),
                    omitted(sorted)));
        });
        siteStats.sort(SITES_ORDER);

        return new ThrowAnalysis(List.copyOf(typeStats), List.copyOf(siteStats), Map.copyOf(siteStacks));
    }

    private static Long commonestStack(Map<Long, Long> countsByStack) {
        return countsByStack.entrySet().stream()
                .max(Entry.<Long, Long>comparingByValue().thenComparing(Entry.comparingByKey()))
                .map(Entry::getKey)
                .orElse(null);
    }

    private static List<ExceptionMessageCount> sortedMessages(Map<String, Long> messages) {
        List<ExceptionMessageCount> sorted = new ArrayList<>(messages.size());
        messages.forEach((message, count) -> sorted.add(new ExceptionMessageCount(message, count)));
        sorted.sort(MESSAGES_ORDER);
        return sorted;
    }

    private static List<ExceptionMessageCount> capped(List<ExceptionMessageCount> sorted) {
        return List.copyOf(sorted.subList(0, Math.min(MESSAGES_LIMIT, sorted.size())));
    }

    private static int omitted(List<ExceptionMessageCount> sorted) {
        return Math.max(0, sorted.size() - MESSAGES_LIMIT);
    }
}
