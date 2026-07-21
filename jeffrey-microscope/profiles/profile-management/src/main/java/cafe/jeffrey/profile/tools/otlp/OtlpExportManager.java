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
import cafe.jeffrey.otlpparser.OtlpProfileWriter.ProfileEntry;
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

    /**
     * One event type chosen for export and whether to export its weight metric (vs the sample count).
     *
     * @param eventType     event type code (e.g. {@code jdk.ExecutionSample})
     * @param includeWeight export the weight dimension instead of the sample count (honored only when the
     *                      event actually carries a weight)
     */
    public record OtlpExportSelection(String eventType, boolean includeWeight) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(OtlpExportManager.class);

    // OTLP has no naming convention for sample types, so a profile's type carries the source JFR event code
    // verbatim (e.g. "jdk.ObjectAllocationInNewTLAB"); the dimension is conveyed by the UNIT (count / bytes /
    // nanoseconds). Panels for pprof/OTLP are always rendered by the generic StackSample provider and the
    // flamegraph generator keys purely off the unit for imported sources, so the JFR code is a safe label.
    private static final String UNIT_NANOSECONDS = "nanoseconds";
    private static final String UNIT_BYTES = "bytes";
    private static final String COUNT_UNIT = "count";

    private static final String CATEGORY_CPU = "CPU";
    private static final String CATEGORY_ALLOCATION = "Allocation";
    private static final String CATEGORY_BLOCKING = "Blocking";
    private static final String CATEGORY_WALL = "Wall-Clock";
    private static final String CATEGORY_OTHER = "Other";
    private static final String SAMPLE_TYPE_SEPARATOR = "/";

    // Turns a streamed event's millisecond offset (from frameBasedEventStreamer) into absolute nanos-from-start.
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

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
                    summary.name() + SAMPLE_TYPE_SEPARATOR + COUNT_UNIT));
        }
        return result;
    }

    /**
     * Builds an OpenTelemetry profiles message for a single stack-based event type. Convenience wrapper
     * over {@link #export(List)}.
     *
     * @param eventType     the event type code to export
     * @param includeWeight when {@code true} and the event carries a weight, export its weight metric
     *                      (e.g. {@code /nanoseconds}) instead of the plain count
     * @return the serialized {@code .otlp} bytes
     */
    public byte[] export(String eventType, boolean includeWeight) {
        return export(List.of(new OtlpExportSelection(eventType, includeWeight)));
    }

    /**
     * Builds an OpenTelemetry profiles message carrying one
     * {@link io.opentelemetry.proto.profiles.v1development.Profile} per selected event type — the weight
     * profile when the selection asks for weight and the event carries one, otherwise the count profile. The
     * weight profile already carries the sample count (one event per observation, {@code samples=1}), so a
     * separate count profile is not needed. Both dimensions name their {@code sample_type} after the source
     * event code; only the unit ({@code count} / {@code bytes} / {@code nanoseconds}) differs. All profiles are
     * serialized by one {@link OtlpProfileWriter} so they share a single dictionary.
     *
     * @param selections the event types to export, each with its own weight choice; must be non-empty
     * @return the serialized {@code .otlp} bytes
     */
    public byte[] export(List<OtlpExportSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("At least one event type must be selected for export");
        }

        List<ProfileEntry> entries = new ArrayList<>();
        for (OtlpExportSelection selection : selections) {
            Type type = Type.fromCode(selection.eventType());
            EventSummary summary = eventTypeRepository.eventSummaries(type)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + selection.eventType()));
            if (!summary.hasStacktrace()) {
                throw new IllegalArgumentException("Event type is not stack-based: " + selection.eventType());
            }

            boolean useWeight = selection.includeWeight() && summary.weight() > 0;
            SampleValueType valueType = useWeight ? weightValueType(summary) : countValueType(type);
            entries.add(streamEntry(type, valueType, useWeight));
        }
        return new OtlpProfileWriter().write(entries, timeNanos(), durationNanos(), profileInfo.name());
    }

    /**
     * Streams one dimension of an event type into a {@link ProfileEntry} (its {@link SampleValueType} plus
     * per-stack OTLP samples). Weighted dimensions stream one observation per event so the exact count
     * round-trips; the count dimension uses the smaller per-second buckets whose value already IS the count.
     */
    private ProfileEntry streamEntry(Type type, SampleValueType valueType, boolean useWeight) {
        LOG.info("Exporting OTLP profile: profileId={} eventType={} useWeight={} sampleType={}/{}",
                profileInfo.id(), type.code(), useWeight, valueType.type(), valueType.unit());

        // Both count and weight stream ONE observation per original event (not per-second buckets) so every
        // event keeps its real millisecond timestamp. That preserves the exact sample count AND the sub-second
        // distribution on re-import — bucketing would snap every event to a whole second and flatten the
        // SubSecond view onto millisecond 0.
        EventQueryConfigurer configurer = new EventQueryConfigurer().withEventType(type).withWeight(useWeight);
        OtlpRecordBuilder builder = new OtlpRecordBuilder(valueType, timeNanos(), NANOS_PER_MILLISECOND);
        return eventStreamRepository.frameBasedEventStreamer(configurer, builder);
    }

    /**
     * Collects streamed {@link TimeseriesRecord}s (one per unique stack, each carrying its value buckets)
     * into OTLP samples that keep per-observation timing, then yields them as a {@link ProfileEntry} on
     * {@link #build()} for the writer to serialize alongside the other selected event types.
     */
    private static final class OtlpRecordBuilder implements RecordBuilder<TimeseriesRecord, ProfileEntry> {

        private final SampleValueType valueType;
        private final long timeNanos;
        // Nanos multiplier for the streamed time slot, which for frameBasedEventStreamer is a millisecond
        // offset — so this turns it into an absolute timestamp relative to the profiling start.
        private final long nanosPerTimeUnit;
        private final List<ExportSample> samples = new ArrayList<>();

        private OtlpRecordBuilder(SampleValueType valueType, long timeNanos, long nanosPerTimeUnit) {
            this.valueType = valueType;
            this.timeNanos = timeNanos;
            this.nanosPerTimeUnit = nanosPerTimeUnit;
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
                timestampsNanos[i] = timeNanos + bucket.second() * nanosPerTimeUnit;
                values[i] = bucket.value();
            }
            samples.add(new ExportSample(frames, timestampsNanos, values));
        }

        @Override
        public ProfileEntry build() {
            return new ProfileEntry(valueType, samples);
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

    /**
     * The count dimension's OTLP {@code sample_type}: the source event code verbatim as the {@code type}
     * (so distinct event types stay distinct panels on re-import) with {@code count} as the unit.
     */
    private static SampleValueType countValueType(Type type) {
        return new SampleValueType(type.code(), COUNT_UNIT);
    }

    private static String weightSampleType(EventSummary summary) {
        SampleValueType valueType = weightValueType(summary);
        return valueType.type() + SAMPLE_TYPE_SEPARATOR + valueType.unit();
    }

    /**
     * The weight dimension's OTLP {@code sample_type}: the source event code verbatim as the {@code type}
     * (matching the count dimension), with the unit chosen from the event's category — allocation weighs in
     * {@code bytes}, everything else (CPU / blocking / wall) in {@code nanoseconds}.
     */
    private static SampleValueType weightValueType(EventSummary summary) {
        String unit = CATEGORY_ALLOCATION.equals(classify(summary)) ? UNIT_BYTES : UNIT_NANOSECONDS;
        return new SampleValueType(summary.name(), unit);
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
