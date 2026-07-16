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

package cafe.jeffrey.tools.jfrotlp;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.profiles.v1development.Profile;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import io.opentelemetry.proto.profiles.v1development.ResourceProfiles;
import io.opentelemetry.proto.profiles.v1development.Sample;
import io.opentelemetry.proto.profiles.v1development.ScopeProfiles;
import io.opentelemetry.proto.profiles.v1development.ValueType;
import io.opentelemetry.proto.resource.v1.Resource;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone CLI converting a JDK Flight Recording (JFR) into the OpenTelemetry profiles signal
 * ({@code opentelemetry.proto.profiles.v1development}, a single serialized {@code ProfilesData}
 * message).
 * <p>
 * Unlike async-profiler's {@code jfr-converter}, this converter sets the
 * {@code profile.frame.type} semantic-convention attribute ({@code jvm} / {@code native} /
 * {@code kernel}) on every location, derived from the JFR frame type — so downstream consumers can
 * distinguish Java from native/kernel frames instead of rendering everything as native.
 * <p>
 * Usage:
 * <pre>
 *   java -jar jfr-otlp-converter.jar &lt;input.jfr&gt; &lt;output.otlp&gt; [--event cpu|alloc|lock] [--service-name NAME]
 * </pre>
 */
public final class JfrToOtlpConverter {

    private static final String OPT_EVENT = "--event";
    private static final String OPT_SERVICE_NAME = "--service-name";

    private static final String SEMCONV_FRAME_TYPE = "profile.frame.type";
    private static final String SEMCONV_THREAD_NAME = "thread.name";
    private static final String SEMCONV_SERVICE_NAME = "service.name";

    private static final String FRAME_TYPE_JVM = "jvm";
    private static final String FRAME_TYPE_NATIVE = "native";
    private static final String FRAME_TYPE_KERNEL = "kernel";

    private static final String SCOPE_NAME = "jeffrey-jfr-otlp-converter";
    private static final String SCOPE_VERSION = "1.0";

    private JfrToOtlpConverter() {
    }

    public static void main(String[] args) throws IOException {
        Options options = Options.parse(args);
        if (options == null) {
            printUsage();
            System.exit(1);
            return;
        }

        long start = System.currentTimeMillis();
        ConversionResult result = convert(options);
        long elapsedMs = System.currentTimeMillis() - start;

        System.out.printf(
                "Converted %s -> %s%n  event=%s samples=%d stacks=%d functions=%d locations=%d skipped_no_stack=%d bytes=%d elapsed_ms=%d%n",
                options.input(), options.output(), options.event().id,
                result.samples(), result.stacks(), result.functions(), result.locations(),
                result.skippedNoStack(), result.outputBytes(), elapsedMs);
    }

    static ConversionResult convert(Options options) throws IOException {
        Dictionary dictionary = new Dictionary();

        // one attribute per semconv frame-type value, reused across all locations
        int jvmFrameAttr = dictionary.stringAttribute(SEMCONV_FRAME_TYPE, FRAME_TYPE_JVM);
        int nativeFrameAttr = dictionary.stringAttribute(SEMCONV_FRAME_TYPE, FRAME_TYPE_NATIVE);
        int kernelFrameAttr = dictionary.stringAttribute(SEMCONV_FRAME_TYPE, FRAME_TYPE_KERNEL);

        EventCategory category = options.event();
        List<Sample> samples = new ArrayList<>();

        long minTimeNanos = Long.MAX_VALUE;
        long maxTimeNanos = Long.MIN_VALUE;
        long skippedNoStack = 0;

        try (RecordingFile recordingFile = new RecordingFile(options.input())) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                if (!category.matches(event.getEventType().getName())) {
                    continue;
                }
                RecordedStackTrace stackTrace = event.getStackTrace();
                if (stackTrace == null || stackTrace.getFrames().isEmpty()) {
                    skippedNoStack++;
                    continue;
                }

                int stackIndex = dictionary.stack(mapStack(stackTrace, dictionary,
                        jvmFrameAttr, nativeFrameAttr, kernelFrameAttr));

                long value = category.value(event);
                long timeNanos = toEpochNanos(event.getStartTime());
                minTimeNanos = Math.min(minTimeNanos, timeNanos);
                maxTimeNanos = Math.max(maxTimeNanos, timeNanos);

                Sample.Builder sample = Sample.newBuilder()
                        .setStackIndex(stackIndex)
                        .addValues(value)
                        .addTimestampsUnixNano(timeNanos);

                int threadAttr = threadAttribute(event.getThread(), dictionary);
                if (threadAttr > 0) {
                    sample.addAttributeIndices(threadAttr);
                }
                samples.add(sample.build());
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                    "No '" + category.id + "' samples with stack traces found in " + options.input());
        }

        long timeUnixNano = minTimeNanos == Long.MAX_VALUE ? 0 : minTimeNanos;
        long durationNano = maxTimeNanos > minTimeNanos ? maxTimeNanos - minTimeNanos : 0;

        Profile profile = Profile.newBuilder()
                .setSampleType(ValueType.newBuilder()
                        .setTypeStrindex(dictionary.string(category.sampleType))
                        .setUnitStrindex(dictionary.string(category.sampleUnit)))
                .addAllSamples(samples)
                .setTimeUnixNano(timeUnixNano)
                .setDurationNano(durationNano)
                .build();

        ProfilesData data = ProfilesData.newBuilder()
                .addResourceProfiles(ResourceProfiles.newBuilder()
                        .setResource(Resource.newBuilder()
                                .addAttributes(stringKeyValue(SEMCONV_SERVICE_NAME, options.serviceName())))
                        .addScopeProfiles(ScopeProfiles.newBuilder()
                                .setScope(InstrumentationScope.newBuilder()
                                        .setName(SCOPE_NAME)
                                        .setVersion(SCOPE_VERSION))
                                .addProfiles(profile)))
                .setDictionary(dictionary.build())
                .build();

        byte[] serialized = data.toByteArray();
        try (OutputStream output = Files.newOutputStream(options.output())) {
            output.write(serialized);
        }

        return new ConversionResult(
                samples.size(), dictionary.stackCount(), dictionary.functionCount(),
                dictionary.locationCount(), skippedNoStack, serialized.length);
    }

    /**
     * Maps a JFR stack trace to leaf-first OTLP location indices. JFR frames are already leaf-first,
     * matching the OTLP {@code Stack} convention, so no reversal is needed.
     */
    private static List<Integer> mapStack(
            RecordedStackTrace stackTrace,
            Dictionary dictionary,
            int jvmFrameAttr,
            int nativeFrameAttr,
            int kernelFrameAttr) {

        List<RecordedFrame> frames = stackTrace.getFrames();
        List<Integer> locationIndices = new ArrayList<>(frames.size());
        for (RecordedFrame frame : frames) {
            RecordedMethod method = frame.getMethod();
            String className = method != null && method.getType() != null ? method.getType().getName() : "";
            String methodName = method != null ? method.getName() : "unknown";
            int functionIndex = dictionary.function(className, methodName);

            int frameTypeAttr = switch (semconvFrameType(frame.getType())) {
                case FRAME_TYPE_JVM -> jvmFrameAttr;
                case FRAME_TYPE_KERNEL -> kernelFrameAttr;
                default -> nativeFrameAttr;
            };

            long line = frame.getLineNumber();
            locationIndices.add(dictionary.location(functionIndex, line, frameTypeAttr));
        }
        return locationIndices;
    }

    /**
     * Maps a JFR frame type ({@link RecordedFrame#getType()}) to a {@code profile.frame.type}
     * semantic-convention value. OTLP has only a single {@code jvm} value, so the JFR JVM
     * sub-types (interpreted / JIT / inlined / C1) all collapse to {@code jvm}.
     */
    private static String semconvFrameType(String jfrFrameType) {
        if (jfrFrameType == null) {
            return FRAME_TYPE_NATIVE;
        }
        return switch (jfrFrameType) {
            case "Interpreted", "JIT compiled", "Inlined", "C1 compiled" -> FRAME_TYPE_JVM;
            case "Kernel" -> FRAME_TYPE_KERNEL;
            default -> FRAME_TYPE_NATIVE;
        };
    }

    private static int threadAttribute(RecordedThread thread, Dictionary dictionary) {
        if (thread == null) {
            return 0;
        }
        String name = thread.getJavaName() != null ? thread.getJavaName() : thread.getOSName();
        if (name == null || name.isBlank()) {
            name = "[tid=" + thread.getOSThreadId() + "]";
        }
        return dictionary.stringAttribute(SEMCONV_THREAD_NAME, name);
    }

    private static KeyValue stringKeyValue(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static void printUsage() {
        System.err.println("""
                Usage: java -jar jfr-otlp-converter.jar <input.jfr> <output.otlp> [options]

                Converts a JDK Flight Recording into an OpenTelemetry profiles (.otlp) file,
                setting the profile.frame.type attribute (jvm/native/kernel) on every frame.

                Options:
                  --event cpu|alloc|lock   Which JFR events to convert (default: cpu)
                  --service-name NAME      Value of the resource service.name attribute
                                           (default: the input file name without extension)""");
    }

    /**
     * The category of JFR events to convert, with the OTLP sample type/unit and per-event value.
     */
    enum EventCategory {
        CPU("cpu", "cpu", "count",
                List.of("jdk.ExecutionSample", "jdk.NativeMethodSample")) {
            @Override
            long value(RecordedEvent event) {
                return 1;
            }
        },
        ALLOC("alloc", "alloc", "bytes",
                List.of("jdk.ObjectAllocationSample", "jdk.ObjectAllocationInNewTLAB",
                        "jdk.ObjectAllocationOutsideTLAB")) {
            @Override
            long value(RecordedEvent event) {
                return firstLongField(event, "weight", "allocationSize", "tlabSize");
            }
        },
        LOCK("lock", "lock", "nanoseconds",
                List.of("jdk.JavaMonitorEnter", "jdk.JavaMonitorWait", "jdk.ThreadPark")) {
            @Override
            long value(RecordedEvent event) {
                return event.getDuration() != null ? event.getDuration().toNanos() : 0;
            }
        };

        private final String id;
        private final String sampleType;
        private final String sampleUnit;
        private final List<String> eventTypeNames;

        EventCategory(String id, String sampleType, String sampleUnit, List<String> eventTypeNames) {
            this.id = id;
            this.sampleType = sampleType;
            this.sampleUnit = sampleUnit;
            this.eventTypeNames = eventTypeNames;
        }

        abstract long value(RecordedEvent event);

        boolean matches(String eventTypeName) {
            return eventTypeNames.contains(eventTypeName);
        }

        static EventCategory fromId(String id) {
            for (EventCategory category : values()) {
                if (category.id.equalsIgnoreCase(id)) {
                    return category;
                }
            }
            return null;
        }

        private static long firstLongField(RecordedEvent event, String... fields) {
            for (String field : fields) {
                if (event.hasField(field)) {
                    return event.getLong(field);
                }
            }
            return 1;
        }
    }

    /**
     * Parsed command-line options.
     */
    record Options(Path input, Path output, EventCategory event, String serviceName) {

        static Options parse(String[] args) {
            Path input = null;
            Path output = null;
            EventCategory event = EventCategory.CPU;
            String serviceName = null;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case OPT_EVENT -> {
                        if (++i >= args.length) {
                            return null;
                        }
                        event = EventCategory.fromId(args[i]);
                        if (event == null) {
                            return null;
                        }
                    }
                    case OPT_SERVICE_NAME -> {
                        if (++i >= args.length) {
                            return null;
                        }
                        serviceName = args[i];
                    }
                    default -> {
                        if (input == null) {
                            input = Path.of(arg);
                        } else if (output == null) {
                            output = Path.of(arg);
                        } else {
                            return null;
                        }
                    }
                }
            }

            if (input == null || output == null) {
                return null;
            }
            if (serviceName == null) {
                serviceName = defaultServiceName(input);
            }
            return new Options(input, output, event, serviceName);
        }

        private static String defaultServiceName(Path input) {
            String fileName = input.getFileName().toString();
            int dot = fileName.indexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
    }

    record ConversionResult(
            int samples, int stacks, int functions, int locations, long skippedNoStack, int outputBytes) {
    }
}
