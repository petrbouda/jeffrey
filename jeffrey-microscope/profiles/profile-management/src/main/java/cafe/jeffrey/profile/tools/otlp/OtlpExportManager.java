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

package cafe.jeffrey.profile.tools.otlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.otlpparser.OtlpProfileWriter;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.ExportFrame;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.ExportSample;
import cafe.jeffrey.otlpparser.OtlpProfileWriter.SampleValueType;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.SecondValue;
import cafe.jeffrey.provider.profile.api.TimeseriesRecord;
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
 * Exports a single stack-based event type of a profile as a standard OpenTelemetry profiles
 * ({@code .otlp}) message. It streams the per-stack, per-second timeseries ({@link TimeseriesRecord}) so
 * every stack's observations keep their timing, and feeds {@link OtlpProfileWriter} one OTLP
 * {@code sample_type} — either the sample count ({@code samples}/{@code count}) or, when weight is
 * requested, the weight metric ({@code cpu}/{@code nanoseconds}, {@code alloc}/{@code bytes}, …). Each
 * frame carries its Jeffrey frame type so the writer can stamp the {@code profile.frame.type}
 * semantic-convention attribute — the interoperability detail that lets downstream consumers tell Java
 * from native/kernel frames.
 * <p>
 * Preserving per-observation timestamps (which pprof cannot) means the exported profile's timeseries and
 * sub-second views reconstruct faithfully on re-import, instead of collapsing to the profile start.
 */
public class OtlpExportManager {

    @FunctionalInterface
    public interface Factory extends Function<ProfileInfo, OtlpExportManager> {
    }

    /**
     * One stack-based event type offered for export.
     *
     * @param code             event type code (e.g. {@code jdk.ExecutionSample})
     * @param label            human-readable label
     * @param samples          total sample count
     * @param weight           total weight, or {@code null} when the event has none
     * @param hasWeight        whether a weight dimension can be added to the OTLP profile
     * @param weightSampleType the weight dimension's OTLP {@code type/unit} (e.g. {@code cpu/nanoseconds},
     *                         {@code alloc/bytes}), or {@code null} when the event has no weight
     * @param category         short category chip (CPU / Allocation / Blocking / Wall-Clock / Other)
     * @param sampleType       the OTLP {@code sample_type} the count dimension maps to
     */
    public record OtlpExportEventType(
            String code,
            String label,
            long samples,
            Long weight,
            boolean hasWeight,
            String weightSampleType,
            String category,
            String sampleType) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(OtlpExportManager.class);

    private static final String UNIT_NANOSECONDS = "nanoseconds";
    // OTLP-idiomatic sample_type names per metric so a re-import maps cleanly onto otel.* event types.
    private static final SampleValueType COUNT_VALUE_TYPE = new SampleValueType("samples", "count");
    private static final SampleValueType ALLOCATION_WEIGHT_TYPE = new SampleValueType("alloc", "bytes");
    private static final SampleValueType CPU_WEIGHT_TYPE = new SampleValueType("cpu", UNIT_NANOSECONDS);
    private static final SampleValueType BLOCKING_WEIGHT_TYPE = new SampleValueType("lock", UNIT_NANOSECONDS);
    private static final SampleValueType WALL_WEIGHT_TYPE = new SampleValueType("wall", UNIT_NANOSECONDS);
    private static final SampleValueType DURATION_WEIGHT_TYPE = new SampleValueType("lock", UNIT_NANOSECONDS);

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

    public OtlpExportManager(
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
    public List<OtlpExportEventType> stackBasedEventTypes() {
        List<OtlpExportEventType> result = new ArrayList<>();
        for (EventSummary summary : eventTypeRepository.eventSummaries()) {
            if (!summary.hasStacktrace()) {
                continue;
            }
            boolean hasWeight = summary.weight() > 0;
            result.add(new OtlpExportEventType(
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
     * Builds an OpenTelemetry profiles message for a single stack-based event type.
     *
     * @param eventType     the event type code to export
     * @param includeWeight when {@code true} and the event carries a weight, export the weight metric
     *                      (e.g. {@code cpu}/{@code nanoseconds}) instead of the sample count
     * @return the serialized {@code .otlp} bytes
     */
    public byte[] export(String eventType, boolean includeWeight) {
        Type type = Type.fromCode(eventType);
        EventSummary summary = eventTypeRepository.eventSummaries(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventType));
        if (!summary.hasStacktrace()) {
            throw new IllegalArgumentException("Event type is not stack-based: " + eventType);
        }

        boolean useWeight = includeWeight && summary.weight() > 0;
        SampleValueType valueType = useWeight ? weightValueType(summary) : COUNT_VALUE_TYPE;

        LOG.info("Exporting OTLP: profileId={} eventType={} useWeight={} sampleType={}/{}",
                profileInfo.id(), eventType, useWeight, valueType.type(), valueType.unit());

        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(type).withWeight(useWeight);
        return eventStreamRepository.frameBasedTimeseriesStreamer(
                configurer,
                new OtlpRecordBuilder(valueType, timeNanos(), durationNanos(), profileInfo.name()));
    }

    /**
     * Collects streamed {@link TimeseriesRecord}s (one per unique stack, each carrying its per-second
     * value buckets) into OTLP samples that keep per-observation timing, then hands them to
     * {@link OtlpProfileWriter} on {@link #build()}.
     */
    private static final class OtlpRecordBuilder implements RecordBuilder<TimeseriesRecord, byte[]> {

        // frameBasedTimeseriesStreamer buckets observations into whole seconds; convert a bucket index
        // back to an absolute timestamp relative to the profiling start.
        private static final long NANOS_PER_BUCKET = 1_000_000_000L;

        private final SampleValueType valueType;
        private final long timeNanos;
        private final long durationNanos;
        private final String serviceName;
        private final List<ExportSample> samples = new ArrayList<>();

        private OtlpRecordBuilder(
                SampleValueType valueType,
                long timeNanos,
                long durationNanos,
                String serviceName) {

            this.valueType = valueType;
            this.timeNanos = timeNanos;
            this.durationNanos = durationNanos;
            this.serviceName = serviceName;
        }

        @Override
        public void onRecord(TimeseriesRecord record) {
            JfrStackTrace stackTrace = record.stacktrace();
            if (stackTrace == null || stackTrace.frames().isEmpty() || record.values().isEmpty()) {
                return;
            }
            List<ExportFrame> frames = new ArrayList<>(stackTrace.frames().size());
            for (JfrStackFrame frame : stackTrace.frames()) {
                frames.add(new ExportFrame(
                        frame.method().className(),
                        frame.method().methodName(),
                        frame.lineNumber(),
                        frame.type()));
            }
            List<SecondValue> buckets = record.values();
            long[] timestampsNanos = new long[buckets.size()];
            long[] values = new long[buckets.size()];
            for (int i = 0; i < buckets.size(); i++) {
                SecondValue bucket = buckets.get(i);
                timestampsNanos[i] = timeNanos + bucket.second() * NANOS_PER_BUCKET;
                values[i] = bucket.value();
            }
            samples.add(new ExportSample(frames, timestampsNanos, values));
        }

        @Override
        public byte[] build() {
            return new OtlpProfileWriter().write(valueType, samples, timeNanos, durationNanos, serviceName);
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
