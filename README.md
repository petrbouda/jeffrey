# Flamegraph-based tool for investigation of JFR profiles

## Literature

- ASPROF Official: https://github.com/async-profiler/async-profiler
- ASPROF Manual: https://krzysztofslusarski.github.io/2022/12/12/async-manual.html
- PERF vs. JFR: https://bell-sw.com/announcements/2022/04/07/how-to-use-perf-to-monitor-java-performance/ 
- Counting CPU samples: https://github.com/async-profiler/async-profiler/discussions/466
- CPU vs. itimer samples: https://github.com/async-profiler/async-profiler/issues/272
- Info about ctimer in golang: https://felixge.de/2022/02/11/profiling-improvements-in-go-1.18/
- DebugNonSafepoints: https://jpbempel.github.io/2022/06/22/debug-non-safepoints.html
    - DebugNonSafepoints may affect accuracy of the topmost virtual frames, but not a frame at the bottom of the stack
- Context-switches in ASPROF: https://github.com/async-profiler/async-profiler/issues/639
- Timing Differences between LOGS and JFR: https://mail.openjdk.org/pipermail/hotspot-gc-dev/2020-August/030581.html
- Frame pointers vs. DWARF: https://rwmj.wordpress.com/2023/02/14/frame-pointers-vs-dwarf-my-verdict
- DWARF in eBPF: https://www.polarsignals.com/blog/posts/2022/11/29/dwarf-based-stack-walking-us

## Tuning

- https://inside.java/

**TimerSlack**
- https://bugs.openjdk.org/browse/JDK-8307766
- https://hazelcast.com/blog/locksupport-parknanos-under-the-hood-and-the-curious-case-of-parking/
- https://github.com/openjdk/jdk/blob/ac968c36d7cc2e13270d28c9310178f6b654d7dc/src/hotspot/os/linux/globals_linux.hpp#L76

**JVM Virtual Calls**
- https://github.com/async-profiler/async-profiler/issues/736

**JVM Logging**
- https://blog.arkey.fr/2020/07/28/embracing-jvm-unified-logging-jep-158-jep-271/

**Garbage Collection**
- All about Garbage Collection changes: https://tschatzl.github.io/
- Region pinning to avoid `GCLocker initiated Garbage Collection`: https://www.infoq.com/news/2023/12/region-pinning-to-g1-gc/
- G1 Pre-barrier: https://albertnetymk.github.io/2022/07/22/g1_barrier

**Java Flight Recorder**
- All JFR Events: https://sap.github.io/SapMachine/jfrevents/
- JFR View command: https://egahlin.github.io/2023/05/30/views.html
- JFR Remote Streaming: https://egahlin.github.io/2021/05/17/remote-recording-stream.html
- JFR CLI Configuration: https://egahlin.github.io/2022/05/31/improved-ergonomics.html

**JVM JIT**
- JIT thresholds: https://www.lmax.com/blog/staff-blogs/2016/03/05/observing-jvm-warm-effects/
- JIT print format: https://github.com/openjdk/jdk/blob/0cf1a558bacf18d9fc41e43fb5e9eba39dc51f2e/src/hotspot/share/compiler/compileTask.cpp#L410-L452
- JIT Directives: https://mostlynerdless.de/blog/2023/05/18/mastering-the-art-of-controlling-the-jit-unlocking-reproducible-profiler-tests/

**Metaspace**
- https://blogs.sap.com/2021/07/16/jep-387-elastic-metaspace-a-new-classroom-for-the-java-virtual-machine/?s=03

**OpenJDK Common Info**
- Hotspot Options: https://chriswhocodes.com/hotspot_options_openjdk18.html
- OpenJDK Standard Library DIFF: https://javaalmanac.io/
- All JEPS: https://chriswhocodes.com/jepsearch.html

**Others**
- Trusted final fields: https://github.com/openjdk/jdk/blob/126f2ace5d36c72a8fced27218a66e0b6f920f85/src/hotspot/share/ci/ciField.cpp#L219-L254
- Off-heap analysis: https://blog.arkey.fr/2020/11/30/off-heap-reconnaissance/
- Object Headers: https://bugs.openjdk.org/browse/JDK-8294992
- Heap Trimming: https://github.com/openjdk/jdk/pull/10085
- Enabling Huge Pages: https://wiki.openjdk.org/display/zgc#Main-EnablingLargePagesOnLinux

## Inspired by

Big thanks to these projects and the developers working on them:
- https://github.com/Netflix/flamescope
- https://github.com/async-profiler/async-profiler
- https://github.com/openjdk/jmc
- https://github.com/spiermar/d3-flame-graph
- https://github.com/spiermar/d3-heatmap2
