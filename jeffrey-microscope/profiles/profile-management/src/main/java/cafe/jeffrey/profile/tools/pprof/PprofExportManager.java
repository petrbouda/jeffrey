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

package cafe.jeffrey.profile.tools.pprof;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.pprofparser.PprofProfileWriter;
import cafe.jeffrey.pprofparser.PprofProfileWriter.ExportFrame;
import cafe.jeffrey.pprofparser.PprofProfileWriter.ExportSample;
import cafe.jeffrey.pprofparser.PprofProfileWriter.SampleValueType;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Exports a single stack-based event type of a profile as a standard, gzip-compressed pprof
 * ({@code .pb.gz}). Streaming one {@link FlamegraphRecord} (one aggregated stack) at a time, it
 * feeds {@link PprofProfileWriter} exactly one JFR event type → one pprof {@code sample_type}
 * ({@code samples}/{@code count}), optionally plus a weight dimension. Keeping a single event type
 * per file avoids the cross-dimension merge that generic JFR→pprof tools produce.
 */
public class PprofExportManager {

    @FunctionalInterface
    public interface Factory extends Function<ProfileInfo, PprofExportManager> {
    }

    /**
     * One stack-based event type offered for export.
     *
     * @param code       event type code (e.g. {@code jdk.ExecutionSample})
     * @param label      human-readable label
     * @param samples    total sample count
     * @param weight     total weight, or {@code null} when the event has none
     * @param hasWeight        whether a weight dimension can be added to the pprof
     * @param weightSampleType the weight dimension's pprof {@code type/unit} (e.g. {@code
     *                         cpu/nanoseconds}, {@code alloc_space/bytes}), or {@code null} when
     *                         the event has no weight
     * @param category         short category chip (CPU / Allocation / Blocking / Wall-Clock / Other)
     * @param sampleType       the pprof {@code sample_type} the count dimension maps to
     */
    public record PprofExportEventType(
            String code,
            String label,
            long samples,
            Long weight,
            boolean hasWeight,
            String weightSampleType,
            String category,
            String sampleType) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(PprofExportManager.class);

    private static final String UNIT_NANOSECONDS = "nanoseconds";
    // Standard pprof sample_type names per metric so the file reads idiomatically in pprof tooling.
    private static final SampleValueType COUNT_VALUE_TYPE = new SampleValueType("samples", "count");
    private static final SampleValueType ALLOCATION_WEIGHT_TYPE = new SampleValueType("alloc_space", "bytes");
    private static final SampleValueType CPU_WEIGHT_TYPE = new SampleValueType("cpu", UNIT_NANOSECONDS);
    private static final SampleValueType BLOCKING_WEIGHT_TYPE = new SampleValueType("delay", UNIT_NANOSECONDS);
    private static final SampleValueType WALL_WEIGHT_TYPE = new SampleValueType("wall", UNIT_NANOSECONDS);
    private static final SampleValueType DURATION_WEIGHT_TYPE = new SampleValueType("delay", UNIT_NANOSECONDS);

    private static final String CATEGORY_CPU = "CPU";
    private static final String CATEGORY_ALLOCATION = "Allocation";
    private static final String CATEGORY_BLOCKING = "Blocking";
    private static final String CATEGORY_WALL = "Wall-Clock";
    private static final String CATEGORY_OTHER = "Other";
    private static final String SAMPLE_TYPE_SEPARATOR = "/";
    private static final String SAMPLE_TYPE_LABEL = "samples" + SAMPLE_TYPE_SEPARATOR + "count";

    private final ProfileInfo profileInfo;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ProfileEventStreamRepository eventStreamRepository;

    public PprofExportManager(
            ProfileInfo profileInfo,
            ProfileEventTypeRepository eventTypeRepository,
            ProfileEventStreamRepository eventStreamRepository) {

        this.profileInfo = profileInfo;
        this.eventTypeRepository = eventTypeRepository;
        this.eventStreamRepository = eventStreamRepository;
    }

    /**
     * @return the stack-based event types of this profile, for the export picker.
     */
    public List<PprofExportEventType> stackBasedEventTypes() {
        List<PprofExportEventType> result = new ArrayList<>();
        for (EventSummary summary : eventTypeRepository.eventSummaries()) {
            if (!summary.hasStacktrace()) {
                continue;
            }
            boolean hasWeight = summary.weight() > 0;
            result.add(new PprofExportEventType(
                    summary.name(),
                    summary.label(),
                    summary.samples(),
                    hasWeight ? summary.weight() : null,
                    hasWeight,
                    hasWeight ? weightSampleType(summary) : null,
                    classify(summary),
                    SAMPLE_TYPE_LABEL));
        }
        return result;
    }

    /**
     * Builds a gzip-compressed pprof for a single stack-based event type.
     *
     * @param eventType     the event type code to export
     * @param includeWeight when {@code true} and the event carries a weight, add a second value type
     * @return the {@code .pb.gz} bytes
     */
    public byte[] export(String eventType, boolean includeWeight) {
        Type type = Type.fromCode(eventType);
        EventSummary summary = eventTypeRepository.eventSummaries(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventType));
        if (!summary.hasStacktrace()) {
            throw new IllegalArgumentException("Event type is not stack-based: " + eventType);
        }

        boolean withWeight = includeWeight && summary.weight() > 0;
        List<SampleValueType> valueTypes = new ArrayList<>();
        valueTypes.add(COUNT_VALUE_TYPE);
        if (withWeight) {
            valueTypes.add(weightValueType(summary));
        }

        LOG.info("Exporting pprof: profileId={} eventType={} includeWeight={} valueTypes={}",
                profileInfo.id(), eventType, withWeight, valueTypes.size());

        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(type);
        return eventStreamRepository.flamegraphStreamer(
                configurer,
                new PprofRecordBuilder(valueTypes, withWeight, timeNanos(), durationNanos()));
    }

    /**
     * Collects streamed {@link FlamegraphRecord}s (one per aggregated stack) into pprof samples,
     * then hands them to {@link PprofProfileWriter} on {@link #build()}.
     */
    private static final class PprofRecordBuilder implements RecordBuilder<FlamegraphRecord, byte[]> {

        private final List<SampleValueType> valueTypes;
        private final boolean withWeight;
        private final long timeNanos;
        private final long durationNanos;
        private final List<ExportSample> samples = new ArrayList<>();

        private PprofRecordBuilder(List<SampleValueType> valueTypes, boolean withWeight, long timeNanos, long durationNanos) {
            this.valueTypes = valueTypes;
            this.withWeight = withWeight;
            this.timeNanos = timeNanos;
            this.durationNanos = durationNanos;
        }

        @Override
        public void onRecord(FlamegraphRecord record) {
            JfrStackTrace stackTrace = record.stackTrace();
            if (stackTrace == null || stackTrace.frames().isEmpty()) {
                return;
            }
            List<ExportFrame> frames = new ArrayList<>(stackTrace.frames().size());
            for (JfrStackFrame frame : stackTrace.frames()) {
                frames.add(new ExportFrame(frame.method().className(), frame.method().methodName(), frame.lineNumber()));
            }
            long[] values = withWeight
                    ? new long[]{record.samples(), record.weight()}
                    : new long[]{record.samples()};
            samples.add(new ExportSample(frames, values));
        }

        @Override
        public byte[] build() {
            return new PprofProfileWriter().write(valueTypes, samples, timeNanos, durationNanos);
        }
    }

    private long timeNanos() {
        return toEpochNanos(profileInfo.profilingStartedAt());
    }

    private long durationNanos() {
        Instant start = profileInfo.profilingStartedAt();
        Instant finish = profileInfo.profilingFinishedAt();
        if (start == null || finish == null) {
            return 0;
        }
        return java.time.Duration.between(start, finish).toNanos();
    }

    private static long toEpochNanos(Instant instant) {
        if (instant == null) {
            return 0;
        }
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static String weightSampleType(EventSummary summary) {
        SampleValueType valueType = weightValueType(summary);
        return valueType.type() + SAMPLE_TYPE_SEPARATOR + valueType.unit();
    }

    private static SampleValueType weightValueType(EventSummary summary) {
        return switch (classify(summary)) {
            case CATEGORY_ALLOCATION -> ALLOCATION_WEIGHT_TYPE;
            case CATEGORY_CPU -> CPU_WEIGHT_TYPE;
            case CATEGORY_BLOCKING -> BLOCKING_WEIGHT_TYPE;
            case CATEGORY_WALL -> WALL_WEIGHT_TYPE;
            default -> DURATION_WEIGHT_TYPE;
        };
    }

    private static String classify(EventSummary summary) {
        String haystack = (summary.name() + " " + summary.label() + " "
                + String.join(" ", summary.categories())).toLowerCase(Locale.ROOT);
        if (containsAny(haystack, "alloc", "tlab")) {
            return CATEGORY_ALLOCATION;
        }
        if (containsAny(haystack, "monitor", "lock", "block", "park", "contention", "wait")) {
            return CATEGORY_BLOCKING;
        }
        if (containsAny(haystack, "wall", "wallclock")) {
            return CATEGORY_WALL;
        }
        if (containsAny(haystack, "executionsample", "cpu", "execution")) {
            return CATEGORY_CPU;
        }
        return firstCategory(summary).orElse(CATEGORY_OTHER);
    }

    private static Optional<String> firstCategory(EventSummary summary) {
        return summary.categories().stream().filter(c -> c != null && !c.isBlank()).findFirst();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
