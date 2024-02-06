package one;/*
 * Copyright 2020 Andrei Pangin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import one.jfr.ClassRef;
import one.jfr.Dictionary;
import one.jfr.JfrReader;
import one.jfr.MethodRef;
import one.jfr.StackTrace;
import one.jfr.event.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Converts .jfr output produced by async-profiler to HTML Flame Graph.
 */
public class jfr2flame {

    private static final String[] FRAME_SUFFIX = {"_[0]", "_[j]", "_[i]", "", "", "_[k]", "_[1]"};

    private final JfrReader jfr;
    private final Arguments args;

    public jfr2flame(JfrReader jfr, Arguments args) {
        this.jfr = jfr;
        this.args = args;
    }

    public void convert(FlameGraph fg) throws IOException {
        Class<? extends Event> eventClass =
                args.live ? LiveObject.class :
                        args.alloc ? AllocationSample.class :
                                args.lock ? ContendedLock.class : ExecutionSample.class;

        jfr.stopAtNewChunk = true;
        while (!jfr.eof()) {
            convertChunk(fg, eventClass);
        }
    }

    public void convertChunk(final FlameGraph fg, Class<? extends Event> eventClass) throws IOException {
        EventAggregator agg = new EventAggregator(args.threads, args.total);

        long threadStates = 0;
        if (args.state != null) {
            for (String state : args.state.split(",")) {
                int key = jfr.getEnumKey("jdk.types.ThreadState", "STATE_" + state.toUpperCase());
                if (key >= 0) threadStates |= 1L << key;
            }
        }

        long startTicks = args.from != 0 ? toTicks(args.from) : Long.MIN_VALUE;
        long endTicks = args.to != 0 ? toTicks(args.to) : Long.MAX_VALUE;

        for (Event event; (event = jfr.readEvent(eventClass)) != null; ) {
            if (event.time >= startTicks && event.time <= endTicks) {
                if (threadStates == 0 || (threadStates & (1L << ((ExecutionSample) event).threadState)) != 0) {
                    agg.collect(event);
                }
            }
        }

        final Dictionary<String> methodNames = new Dictionary<>();
        final Classifier classifier = new Classifier(methodNames);

        final double ticksToNanos = 1e9 / jfr.ticksPerSec;
        final boolean scale = args.total && args.lock && ticksToNanos != 1.0;

        // Don't use lambda for faster startup
        agg.forEach(new EventAggregator.Visitor() {
            @Override
            public void visit(Event event, long value) {
                StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
                if (stackTrace != null) {
                    Arguments args = jfr2flame.this.args;
                    long[] methods = stackTrace.methods;
                    byte[] types = stackTrace.types;
                    int[] locations = stackTrace.locations;
                    String classFrame = getClassFrame(event);
                    String[] trace = new String[methods.length
                            + (args.threads ? 1 : 0)
                            + (args.classify ? 1 : 0)
                            + (classFrame != null ? 1 : 0)];
                    if (args.threads) {
                        trace[0] = getThreadFrame(event.tid);
                    }
                    int idx = trace.length;
                    if (classFrame != null) {
                        trace[--idx] = classFrame;
                    }
                    for (int i = 0; i < methods.length; i++) {
                        String methodName = getMethodName(methodNames, methods[i], types[i]);
                        int location;
                        if (args.lines && (location = locations[i] >>> 16) != 0) {
                            methodName += ":" + location;
                        } else if (args.bci && (location = locations[i] & 0xffff) != 0) {
                            methodName += "@" + location;
                        }
                        trace[--idx] = methodName + FRAME_SUFFIX[types[i]];
                    }
                    if (args.classify) {
                        trace[--idx] = classifier.getCategoryName(stackTrace);
                    }
                    fg.addSample(trace, scale ? (long) (value * ticksToNanos) : value);
                }
            }
        });
    }

    private String getThreadFrame(int tid) {
        String threadName = jfr.threads.get(tid);
        return threadName == null ? "[tid=" + tid + ']' :
                threadName.startsWith("[tid=") ? threadName : '[' + threadName + " tid=" + tid + ']';
    }

    private String getClassFrame(Event event) {
        long classId;
        String suffix;
        switch (event) {
            case AllocationSample allocationSample -> {
                classId = allocationSample.classId;
                suffix = allocationSample.tlabSize == 0 ? "_[k]" : "_[i]";
            }
            case ContendedLock contendedLock -> {
                classId = contendedLock.classId;
                suffix = "_[i]";
            }
            case LiveObject liveObject -> {
                classId = liveObject.classId;
                suffix = "_[i]";
            }
            case null, default -> {
                return null;
            }
        }

        ClassRef cls = jfr.classes.get(classId);
        if (cls == null) {
            return "null";
        }
        byte[] className = jfr.symbols.get(cls.name);

        int arrayDepth = 0;
        while (className[arrayDepth] == '[') {
            arrayDepth++;
        }

        StringBuilder sb = new StringBuilder(toJavaClassName(className, arrayDepth, true));
        while (arrayDepth-- > 0) {
            sb.append("[]");
        }
        return sb.append(suffix).toString();
    }

    private String getMethodName(Dictionary<String> methodNames, long methodId, byte methodType) {
        String result = methodNames.get(methodId);
        if (result != null) {
            return result;
        }

        MethodRef method = jfr.methods.get(methodId);
        if (method == null) {
            result = "unknown";
        } else {
            ClassRef cls = jfr.classes.get(method.cls);
            byte[] className = jfr.symbols.get(cls.name);
            byte[] methodName = jfr.symbols.get(method.name);

            if (className == null || className.length == 0 || isNativeFrame(methodType)) {
                result = new String(methodName, StandardCharsets.UTF_8);
            } else {
                String classStr = toJavaClassName(className, 0, args.dot);
                String methodStr = new String(methodName, StandardCharsets.UTF_8);
                result = methodStr.isEmpty() ? classStr : classStr + '.' + methodStr;
            }
        }

        methodNames.put(methodId, result);
        return result;
    }

    private boolean isNativeFrame(byte methodType) {
        return methodType == FrameType.FRAME_NATIVE && jfr.getEnumValue("jdk.types.FrameType", FrameType.FRAME_KERNEL) != null
                || methodType == FrameType.FRAME_CPP
                || methodType == FrameType.FRAME_KERNEL;
    }

    private String toJavaClassName(byte[] symbol, int start, boolean dotted) {
        int end = symbol.length;
        if (start > 0) {
            switch (symbol[start]) {
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'Z':
                    return "boolean";
                case 'F':
                    return "float";
                case 'D':
                    return "double";
                case 'L':
                    start++;
                    end--;
            }
        }

        if (args.norm) {
            for (int i = end - 2; i > start; i--) {
                if (symbol[i] == '/' || symbol[i] == '.') {
                    if (symbol[i + 1] >= '0' && symbol[i + 1] <= '9') {
                        end = i;
                    }
                    break;
                }
            }
        }

        if (args.simple) {
            for (int i = end - 2; i >= start; i--) {
                if (symbol[i] == '/' && (symbol[i + 1] < '0' || symbol[i + 1] > '9')) {
                    start = i + 1;
                    break;
                }
            }
        }

        String s = new String(symbol, start, end - start, StandardCharsets.UTF_8);
        return dotted ? s.replace('/', '.') : s;
    }

    // millis can be an absolute timestamp or an offset from the beginning/end of the recording
    private long toTicks(long millis) {
        long nanos = millis * 1_000_000;
        if (millis < 0) {
            nanos += jfr.endNanos;
        } else if (millis < 1500000000000L) {
            nanos += jfr.startNanos;
        }
        return jfr.nanosToTicks(nanos);
    }
}
